package com.rightpath.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.rightpath.entity.CandidateInterviewSchedule;
import com.rightpath.entity.InterviewMobilePairing;
import com.rightpath.entity.ProctoringEvent;
import com.rightpath.exceptions.ResourceNotFoundException;
import com.rightpath.repository.CandidateInterviewScheduleRepository;
import com.rightpath.repository.InterviewMobilePairingRepository;
import com.rightpath.repository.ProctoringEventRepository;

/**
 * Ties a phone's pairing token to the interview it is sitting beside, so the
 * phone can send its recording and photos without a login of its own.
 *
 * <p>The candidate's browser registers the token (signed in, and checked
 * against the interview's owner); the phone then quotes it. The token is a
 * random UUID the candidate's browser generated and showed only as a QR code,
 * so it is as hard to guess as any session id. It is also bounded: it stops
 * working after twelve hours, and it can carry a fixed amount of recording, so
 * a leaked one cannot be used to fill the bucket.</p>
 */
@Service
public class MobilePairingService {

    private static final Logger log = LoggerFactory.getLogger(MobilePairingService.class);

    private static final Pattern TOKEN_SHAPE = Pattern.compile("^[A-Za-z0-9-]{16,64}$");

    /** Longer than any interview, with room for one that was started late or resumed. */
    private static final Duration VALID_FOR = Duration.ofHours(12);

    /** An hour of phone video is a few hundred megabytes; this is generous and still finite. */
    private static final long MAX_BYTES = 4L * 1024 * 1024 * 1024;

    private final InterviewMobilePairingRepository pairingRepository;
    private final CandidateInterviewScheduleRepository scheduleRepository;
    private final ProctoringEventRepository proctoringEventRepository;

    public MobilePairingService(InterviewMobilePairingRepository pairingRepository,
            CandidateInterviewScheduleRepository scheduleRepository,
            ProctoringEventRepository proctoringEventRepository) {
        this.pairingRepository = pairingRepository;
        this.scheduleRepository = scheduleRepository;
        this.proctoringEventRepository = proctoringEventRepository;
    }

    /** Bind a token to an interview. The candidate must own the interview. Safe to repeat. */
    public void register(Long scheduleId, String actorEmail, String token) {
        if (token == null || !TOKEN_SHAPE.matcher(token).matches()) {
            throw new IllegalArgumentException("token is not a valid pairing token");
        }
        CandidateInterviewSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview schedule not found: " + scheduleId));
        if (actorEmail == null || actorEmail.isBlank() || schedule.getEmail() == null
                || !schedule.getEmail().equalsIgnoreCase(actorEmail)) {
            throw new AccessDeniedException("Interview " + scheduleId + " is not assigned to the authenticated candidate");
        }

        InterviewMobilePairing existing = pairingRepository.findById(token).orElse(null);
        if (existing != null) {
            if (!existing.getInterviewScheduleId().equals(scheduleId)) {
                // A token already tied to a different interview is never moved.
                throw new AccessDeniedException("This pairing token belongs to another interview");
            }
            return;
        }
        pairingRepository.save(InterviewMobilePairing.builder()
                .token(token)
                .interviewScheduleId(scheduleId)
                .candidateEmail(schedule.getEmail())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());
        log.info("Phone pairing registered for interview {}", scheduleId);
    }

    /** The pairing a token stands for, or a refusal. Unknown and expired look the same to the caller. */
    public InterviewMobilePairing resolve(String token) {
        InterviewMobilePairing pairing = token == null ? null : pairingRepository.findById(token).orElse(null);
        if (pairing == null
                || pairing.getCreatedAt().plus(VALID_FOR).isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new AccessDeniedException("This phone is not paired with an interview");
        }
        return pairing;
    }

    /** Like {@link #resolve}, but for callers that can carry on without a pairing. */
    public InterviewMobilePairing resolveOrNull(String token) {
        try {
            return resolve(token);
        } catch (AccessDeniedException e) {
            return null;
        }
    }

    /** Count recording bytes against the token, refusing once it has had its share. */
    public void accept(InterviewMobilePairing pairing, long bytes) {
        if (pairing.getBytesReceived() + bytes > MAX_BYTES) {
            throw new AccessDeniedException("This pairing has reached its upload limit");
        }
        pairingRepository.addBytes(pairing.getToken(), bytes);
    }

    /** Put what the phone reports on the interview's record, where the reviewer reads it. */
    public void recordEvent(InterviewMobilePairing pairing, String eventType, String details) {
        try {
            proctoringEventRepository.save(ProctoringEvent.builder()
                    .schedule(scheduleRepository.getReferenceById(pairing.getInterviewScheduleId()))
                    .eventType(eventType)
                    .details(details)
                    .build());
        } catch (Exception e) {
            // Reported, not thrown: the phone is mid-interview and a failure to
            // file an audit line must not cost it anything else.
            log.error("Could not record phone event {} for interview {}", eventType, pairing.getInterviewScheduleId(), e);
        }
    }
}
