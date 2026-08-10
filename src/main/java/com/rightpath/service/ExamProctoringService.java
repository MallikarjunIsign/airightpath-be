package com.rightpath.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.rightpath.dto.ProctoringCaptureDto;
import com.rightpath.dto.ProctoringCaptureImage;

/**
 * Stores and serves the images captured on the exam instructions screen, before
 * the exam clock starts.
 *
 * <p>Captures are always recorded against a real assessment attempt owned by the
 * caller, so a reviewer looking at a disputed score can trust that the face in
 * the photo is the account that sat that exam.</p>
 */
public interface ExamProctoringService {

    /**
     * Stores the candidate's identity photo for an attempt, replacing any photo
     * already held for it.
     *
     * @param assessmentId   the assessment about to be attempted
     * @param candidateEmail the candidate named in the request
     * @param capturedAt     ISO-8601 client clock; ignored if unparseable
     * @param photo          the captured frame
     * @param actorEmail     the authenticated caller
     * @return the stored capture
     */
    ProctoringCaptureDto saveIdentityPhoto(String assessmentId, String candidateEmail, String capturedAt,
            MultipartFile photo, String actorEmail);

    /**
     * Stores an ordered room sweep for an attempt, replacing any previous sweep.
     * The frame count is whatever the client sent — it is configurable there.
     *
     * @param assessmentId   the assessment about to be attempted
     * @param candidateEmail the candidate named in the request
     * @param capturedAt     ISO-8601 client clock; ignored if unparseable
     * @param frames         the sweep frames, in order
     * @param actorEmail     the authenticated caller
     * @return the stored captures, in frame order
     */
    List<ProctoringCaptureDto> saveRoomScan(String assessmentId, String candidateEmail, String capturedAt,
            List<MultipartFile> frames, String actorEmail);

    /**
     * Everything captured for one attempt, for review alongside the result.
     *
     * @param assessmentId the assessment attempt
     * @return ordered captures; empty when nothing was captured
     */
    List<ProctoringCaptureDto> getCaptures(Long assessmentId);

    /**
     * Every capture across a candidate's attempts for one job.
     *
     * @param candidateEmail the candidate
     * @param jobPrefix      the job identifier prefix
     * @return ordered captures; empty when nothing was captured
     */
    List<ProctoringCaptureDto> getCaptures(String candidateEmail, String jobPrefix);

    /**
     * Pulls one capture's bytes back out of storage.
     *
     * @param captureId the capture to fetch
     * @return the image bytes and how to serve them
     */
    ProctoringCaptureImage loadImage(Long captureId);
}
