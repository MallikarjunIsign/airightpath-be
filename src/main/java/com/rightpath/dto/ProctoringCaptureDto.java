package com.rightpath.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rightpath.entity.ExamProctoringCapture;
import com.rightpath.enums.ProctoringCaptureType;

/**
 * A stored pre-exam capture as the admin console sees it.
 *
 * <p>The bytes are not inlined: {@code imageUrl} points at the endpoint that
 * streams them, so a listing of a full room scan stays small.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProctoringCaptureDto(
        Long id,
        Long assessmentId,
        String candidateEmail,
        String jobPrefix,
        ProctoringCaptureType captureType,
        int frameIndex,
        String contentType,
        Long sizeBytes,
        LocalDateTime capturedAt,
        LocalDateTime uploadedAt,
        String imageUrl
) {

    /**
     * Projects a stored capture, deriving the URL that serves its bytes.
     *
     * @param capture the persisted capture
     * @return the admin-facing view of it
     */
    public static ProctoringCaptureDto from(ExamProctoringCapture capture) {
        return new ProctoringCaptureDto(
                capture.getId(),
                capture.getAssessmentId(),
                capture.getCandidateEmail(),
                capture.getJobPrefix(),
                capture.getCaptureType(),
                capture.getFrameIndex(),
                capture.getContentType(),
                capture.getSizeBytes(),
                capture.getCapturedAt(),
                capture.getUploadedAt(),
                "/api/exam-proctoring/captures/" + capture.getId() + "/image");
    }
}
