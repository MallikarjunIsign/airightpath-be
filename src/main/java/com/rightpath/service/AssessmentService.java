package com.rightpath.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.rightpath.dto.AssessmentUploadDto;
import com.rightpath.dto.AssignAssessmentBlobDto;
import com.rightpath.dto.AssignAssessmentDto;
import com.rightpath.entity.Assessment;
import com.rightpath.entity.Result;

/**
 * Service interface for managing assessments, including uploading, assigning, submitting, 
 * evaluating, and retrieving assessment and result data.
 */
public interface AssessmentService {

    /**
     * Uploads an assessment to the system.
     *
     * @param dto The details of the assessment to be uploaded.
     * @return A message indicating the success or failure of the upload.
     * 
     * Developer Note: Log the assessment type, uploadedBy, and candidateEmail at INFO level.
     */
    String uploadAssessment(AssessmentUploadDto dto);

    /**
     * Assigns an assessment to a candidate with a job-specific prefix.
     *
     * @param dto The assignment details including candidate and assessment information.
     * @param jobPrefix The prefix for the job role or position.
     * @return A message indicating the success or failure of the assignment.
     * 
     * Developer Note: Log assigned assessmentType, candidateEmail, and jobPrefix.
     */
    String assignAssessment(AssignAssessmentDto dto, String jobPrefix);

    /**
     * Retrieves all assessments assigned to a specific candidate.
     *
     * @param candidateEmail The email of the candidate.
     * @return A list of assessments assigned to the candidate.
     * 
     * Developer Note: Log candidateEmail and the count of returned assessments.
     */
    List<Assessment> getCandidateAssessments(String candidateEmail);

    /**
     * Marks an assessment as submitted.
     *
     * @param id The ID of the assessment to be marked as submitted.
     * @return A message indicating the success or failure of the operation.
     * 
     * Developer Note: Log the assessmentId and submission timestamp.
     */
    String submitAssessment(Long id);

    /**
     * Records the result of a candidate's completed assessment.
     *
     * @param candidateEmail The email of the candidate.
     * @param assessmentType The type of assessment completed.
     * @param score The score obtained by the candidate.
     * @param jsonData The JSON-formatted breakdown or explanation of the score.
     * @param jobPrefix The prefix representing the job category.
     * @return A message indicating result recording status.
     * 
     * Developer Note: Log candidateEmail, assessmentType, score, and jobPrefix.
     */
    String resultAssessment(String candidateEmail, String assessmentType, Double score, String jsonData, String jobPrefix);

    /**
     * Fetches the details of a specific assessment by its ID.
     *
     * @param assessmentId The ID of the assessment.
     * @return The Assessment entity if found.
     * 
     * Developer Note: Log assessmentId and the existence check result.
     */
    Assessment fetchAssessmentDetails(Long assessmentId);

    /**
     * Retrieves assessments for a candidate, with a toggle to fetch all or only active ones.
     *
     * @param candidateEmail The candidate's email.
     * @param fetchAll Whether to fetch all assessments or only active ones.
     * @return A list of assessments.
     * 
     * Developer Note: Log candidateEmail and fetchAll flag.
     */
    List<Assessment> getAssessments(String candidateEmail, boolean fetchAll);

    /**
     * Expires all assessments that have passed their deadline.
     * Updates assessment records to reflect expiration.
     * 
     * Developer Note: Log number of assessments expired and their IDs.
     */
    void expireAssessments();

    /**
     * Retrieves all results recorded for a candidate.
     *
     * @param email The candidate’s email.
     * @return A list of Result entities.
     * 
     * Developer Note: Log the email and result count.
     */
    List<Result> getResultsByEmail(String email);

    /**
     * Fetches a specific Result entity by its ID.
     *
     * @param id The result ID.
     * @return An Optional containing the result if found.
     * 
     * Developer Note: Log the result ID and if found or not.
     */
    Optional<Result> getResultById(Long id);

    /**
     * Retrieves the latest question paper for a candidate based on job prefix and assessment type.
     *
     * @param email The candidate’s email.
     * @param jobPrefix The job prefix.
     * @param assessmentType The type of the assessment.
     * @return A string (file name or download link).
     * 
     * Developer Note: Log the email, jobPrefix, and assessmentType.
     */
    String getLatestQuestionPaper(String email, String jobPrefix, String assessmentType);

    /**
     * Marks that a candidate has attended the exam.
     *
     * @param candidateEmail The email of the candidate.
     * @param assessmentId The ID of the attended assessment.
     * 
     * Developer Note: Log candidateEmail, assessmentId, and attendance timestamp.
     */
    void markExamAsAttended(String candidateEmail, Long assessmentId);

    String assignAssessmentToStorage(AssignAssessmentBlobDto dto);
    String downloadFileContentFromStorage(String containerName, String fileName);

	List<Map<String, Object>> getLatestAssessmentContent(String jobPrefix, String candidateEmail,
			String assessmentType);

	List<Result> getResultsByEmailAndJobPrefix(String email, String jobPrefix);
}
