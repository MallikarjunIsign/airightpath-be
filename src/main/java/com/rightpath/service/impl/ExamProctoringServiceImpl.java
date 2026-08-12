package com.rightpath.service.impl;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.rightpath.dto.ProctoringCaptureDto;
import com.rightpath.dto.ProctoringCaptureImage;
import com.rightpath.entity.Assessment;
import com.rightpath.entity.ExamProctoringCapture;
import com.rightpath.enums.ProctoringCaptureType;
import com.rightpath.exceptions.ResourceNotFoundException;
import com.rightpath.repository.AssessmentRepository;
import com.rightpath.repository.ExamProctoringCaptureRepository;
import com.rightpath.service.ExamProctoringService;
import com.rightpath.service.StorageService;

import jakarta.transaction.Transactional;

/**
 * Persists pre-exam captures to object storage and keeps a pointer row per image.
 *
 * <p>A failure here does block the candidate. Where the client has a capture
 * configured as required, it holds the exam start until the upload succeeds,
 * offering a retry that re-sends the frames already taken — a photo that never
 * reached storage is no evidence of who sat the exam. Errors returned from this
 * service reach someone waiting to start an exam, so they are worth making
 * accurate.</p>
 *
 * <p>What it guarantees is that anything it does store is attributable: the
 * caller must own the assessment attempt they are uploading against.</p>
 */
@Transactional
@Service
public class ExamProctoringServiceImpl implements ExamProctoringService {

    private static final Logger logger = LoggerFactory.getLogger(ExamProctoringServiceImpl.class);

    /** An identity photo is a single frame, so it always occupies slot 0. */
    private static final int IDENTITY_PHOTO_FRAME_INDEX = 0;

    private static final String DEFAULT_CONTENT_TYPE = "image/jpeg";

    @Value("${aws.s3.prefix.proctoring:proctoring}")
    private String proctoringPrefix;

    /** Guards against a client streaming something far larger than a webcam frame. */
    @Value("${proctoring.capture.max-file-size-bytes:5242880}")
    private long maxFileSizeBytes;

    /** The sweep length is client-configurable, so accept a range rather than a fixed count. */
    @Value("${proctoring.room-scan.max-frames:32}")
    private int maxRoomScanFrames;

    private final ExamProctoringCaptureRepository captureRepository;
    private final AssessmentRepository assessmentRepository;
    private final StorageService storageService;

    public ExamProctoringServiceImpl(ExamProctoringCaptureRepository captureRepository,
            AssessmentRepository assessmentRepository, StorageService storageService) {
        this.captureRepository = captureRepository;
        this.assessmentRepository = assessmentRepository;
        this.storageService = storageService;
    }

    @Override
    public ProctoringCaptureDto saveIdentityPhoto(String assessmentId, String candidateEmail, String capturedAt,
            MultipartFile photo, String actorEmail) {

        Assessment assessment = requireOwnedAssessment(assessmentId, candidateEmail, actorEmail);
        validateImage(photo, "photo");

        ExamProctoringCapture capture = store(assessment, ProctoringCaptureType.IDENTITY_PHOTO,
                IDENTITY_PHOTO_FRAME_INDEX, photo, parseCapturedAt(capturedAt));

        logger.info("Stored identity photo for assessment {} (candidate {})", assessment.getId(),
                assessment.getCandidateEmail());
        return ProctoringCaptureDto.from(capture);
    }

    @Override
    public List<ProctoringCaptureDto> saveRoomScan(String assessmentId, String candidateEmail, String capturedAt,
            List<MultipartFile> frames, String actorEmail) {

        Assessment assessment = requireOwnedAssessment(assessmentId, candidateEmail, actorEmail);

        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("At least one room scan frame is required");
        }
        if (frames.size() > maxRoomScanFrames) {
            throw new IllegalArgumentException(
                    "A room scan may contain at most " + maxRoomScanFrames + " frames, received " + frames.size());
        }
        for (int i = 0; i < frames.size(); i++) {
            validateImage(frames.get(i), "frames[" + i + "]");
        }

        // A repeat scan can be shorter than the last one, so clear the old sweep
        // rather than overwriting frame by frame and leaving a stale tail behind.
        captureRepository.deleteByAssessmentIdAndCaptureType(assessment.getId(),
                ProctoringCaptureType.ROOM_SCAN_FRAME);
        captureRepository.flush();

        LocalDateTime captureTime = parseCapturedAt(capturedAt);
        List<ProctoringCaptureDto> stored = new ArrayList<>(frames.size());
        for (int index = 0; index < frames.size(); index++) {
            stored.add(ProctoringCaptureDto.from(store(assessment, ProctoringCaptureType.ROOM_SCAN_FRAME, index,
                    frames.get(index), captureTime)));
        }

        logger.info("Stored {} room scan frames for assessment {} (candidate {})", stored.size(), assessment.getId(),
                assessment.getCandidateEmail());
        return stored;
    }

    @Override
    public List<ProctoringCaptureDto> getCaptures(Long assessmentId) {
        return captureRepository.findByAssessmentIdOrderByCaptureTypeAscFrameIndexAsc(assessmentId).stream()
                .map(ProctoringCaptureDto::from)
                .toList();
    }

    @Override
    public List<ProctoringCaptureDto> getCaptures(String candidateEmail, String jobPrefix) {
        return captureRepository
                .findByCandidateEmailAndJobPrefixOrderByAssessmentIdAscCaptureTypeAscFrameIndexAsc(candidateEmail,
                        jobPrefix)
                .stream()
                .map(ProctoringCaptureDto::from)
                .toList();
    }

    @Override
    public ProctoringCaptureImage loadImage(Long captureId) {
        ExamProctoringCapture capture = captureRepository.findById(captureId)
                .orElseThrow(() -> new ResourceNotFoundException("Proctoring capture not found: " + captureId));

        byte[] bytes = storageService.downloadFile(capture.getContainerName(), capture.getFileName());
        String contentType = capture.getContentType() != null ? capture.getContentType() : DEFAULT_CONTENT_TYPE;
        String downloadName = capture.getCaptureType().name().toLowerCase(Locale.ROOT) + "-" + capture.getAssessmentId()
                + "-" + capture.getFrameIndex() + extensionFor(contentType);

        return new ProctoringCaptureImage(bytes, contentType, downloadName);
    }

    /**
     * Uploads one frame and records (or replaces) the row pointing at it.
     *
     * <p>The storage key is derived from the slot, so a re-capture overwrites the
     * object it replaces instead of leaving an orphan behind in the bucket.</p>
     */
    private ExamProctoringCapture store(Assessment assessment, ProctoringCaptureType type, int frameIndex,
            MultipartFile file, LocalDateTime capturedAt) {

        String contentType = file.getContentType() != null ? file.getContentType() : DEFAULT_CONTENT_TYPE;
        String fileName = storageKey(assessment.getId(), type, frameIndex, contentType);
        storageService.uploadFile(proctoringPrefix, fileName, file);

        ExamProctoringCapture capture = captureRepository
                .findByAssessmentIdAndCaptureTypeAndFrameIndex(assessment.getId(), type, frameIndex)
                .orElseGet(ExamProctoringCapture::new);

        capture.setAssessmentId(assessment.getId());
        capture.setCandidateEmail(assessment.getCandidateEmail());
        capture.setJobPrefix(assessment.getJobPrefix());
        capture.setCaptureType(type);
        capture.setFrameIndex(frameIndex);
        capture.setContainerName(proctoringPrefix);
        capture.setFileName(fileName);
        capture.setContentType(contentType);
        capture.setSizeBytes(file.getSize());
        capture.setCapturedAt(capturedAt);
        // UTC for the same reason as capturedAt — both columns are zone-less.
        capture.setUploadedAt(LocalDateTime.now(ZoneOffset.UTC));

        return captureRepository.save(capture);
    }

    private String storageKey(Long assessmentId, ProctoringCaptureType type, int frameIndex, String contentType) {
        String extension = extensionFor(contentType);
        if (type == ProctoringCaptureType.IDENTITY_PHOTO) {
            return "identity-photo/" + assessmentId + extension;
        }
        return "room-scan/" + assessmentId + "/frame-" + frameIndex + extension;
    }

    private String extensionFor(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    /**
     * Resolves the attempt and refuses anything the caller does not own.
     *
     * <p>Both the assessment and the {@code candidateEmail} in the request have to
     * line up with the authenticated caller — a capture filed against someone
     * else's attempt would be worse than no capture at all.</p>
     */
    private Assessment requireOwnedAssessment(String rawAssessmentId, String candidateEmail, String actorEmail) {
        Long id = parseAssessmentId(rawAssessmentId);

        Assessment assessment = assessmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + id));

        if (actorEmail == null || actorEmail.isBlank()) {
            throw new AccessDeniedException("An authenticated candidate is required to upload a proctoring capture");
        }
        if (candidateEmail == null || !candidateEmail.trim().equalsIgnoreCase(actorEmail)) {
            throw new AccessDeniedException("candidateEmail must match the authenticated candidate");
        }
        if (assessment.getCandidateEmail() == null
                || !assessment.getCandidateEmail().equalsIgnoreCase(actorEmail)) {
            throw new AccessDeniedException("Assessment " + id + " is not assigned to the authenticated candidate");
        }
        return assessment;
    }

    private Long parseAssessmentId(String rawAssessmentId) {
        if (rawAssessmentId == null || rawAssessmentId.isBlank()) {
            throw new IllegalArgumentException("assessmentId is required");
        }
        try {
            return Long.parseLong(rawAssessmentId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("assessmentId must be numeric, received: " + rawAssessmentId);
        }
    }

    private void validateImage(MultipartFile file, String field) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException(
                    field + " exceeds the " + maxFileSizeBytes + " byte limit for a proctoring capture");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException(field + " must be an image, received content type: " + contentType);
        }
    }

    /**
     * Reads the client's capture timestamp, falling back to server time.
     *
     * <p>{@code capturedAt} comes off the browser clock and is informational, so a
     * malformed value is logged and replaced rather than failing an upload that
     * carries a perfectly good photo.</p>
     */
    private LocalDateTime parseCapturedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        String value = raw.trim();
        try {
            // UTC, not ZoneId.systemDefault(). The column is zone-less, so what
            // it means has to be fixed by convention rather than by wherever the
            // server happens to run — otherwise the same instant reads
            // differently after a deploy to another region, and every stored row
            // becomes ambiguous. The DTO republishes these as instants so the
            // browser is never left guessing either.
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Not an offset timestamp; try a local one below.
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            logger.warn("Unparseable capturedAt '{}' on a proctoring capture; recording server time instead", value);
            return LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
