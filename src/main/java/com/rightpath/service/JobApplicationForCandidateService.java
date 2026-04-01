package com.rightpath.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.rightpath.dto.JobApplicationForCandidateDTO;
import com.rightpath.entity.JobApplicationForCandidate;

@Service
public interface JobApplicationForCandidateService {

    /**
     * Applies for a job using the provided candidate DTO.
     *
     * @param jobApplicationForCandidateDTO The job application data submitted by the candidate.
     */
    void applyForJob(JobApplicationForCandidateDTO jobApplicationForCandidateDTO);

    /**
     * Retrieves all job applications.
     *
     * @return A list of all candidate job applications.
     */
    List<JobApplicationForCandidateDTO> getAllApplications();

    /**
     * Retrieves job applications by candidate email.
     *
     * @param email Candidate's email address.
     * @return A list of applications associated with the provided email.
     */
    List<JobApplicationForCandidateDTO> getApplicationsByEmail(String email);

    /**
     * Retrieves job applications by job prefix.
     *
     * @param jobPrefix Unique job prefix identifier.
     * @return A list of job applications related to the given job prefix.
     */
    List<JobApplicationForCandidateDTO> getApplicationsByJobPrefix(String jobPrefix);

    /**
     * Filters candidates for a specific job post ID.
     *
     * @param jobPostId The ID of the job post.
     * @return A list of filtered candidate applications.
     */
    List<JobApplicationForCandidateDTO> filterCandidates(Long jobPostId);

    /**
     * Filters candidates using the job prefix.
     *
     * @param jobPrefix Unique identifier for the job.
     * @return A list of filtered candidate applications by job prefix.
     */
    List<JobApplicationForCandidateDTO> filterCandidatesByPrefix(String jobPrefix);

    /**
     * Retrieves applicants by job post ID.
     *
     * @param jobPostId ID of the job post.
     * @return List of applicants.
     */
    List<JobApplicationForCandidateDTO> getApplicantsByJobPostId(Long jobPostId);

    /**
     * Retrieves shortlisted candidates by job prefix.
     *
     * @param jobPrefix Unique job prefix.
     * @return List of shortlisted candidates.
     */
    List<JobApplicationForCandidateDTO> getShortlistedCandidatesByPrefix(String jobPrefix);

    /**
     * Retrieves rejected candidates by job prefix.
     *
     * @param jobPrefix Unique job prefix.
     * @return List of rejected candidates.
     */
    List<JobApplicationForCandidateDTO> getRejectedCandidatesByPrefix(String jobPrefix);

    /**
     * Retrieves a job application entity by its unique ID.
     *
     * @param id Job application ID.
     * @return JobApplicationForCandidate entity.
     */
    JobApplicationForCandidate getById(Long id);

    /**
     * Sends an acknowledgment email to the candidate and updates the application status.
     *
     * @param jobPrefix Job identifier.
     * @param email Candidate's email.
     * @param date Date of communication.
     * @param time Time of communication.
     */
    void sendAcknowledgementMailAndUpdateStatus(String jobPrefix, String email, String date, String time);

    /**
     * Acknowledges a candidate manually and returns a response.
     *
     * @param jobPrefix Job identifier.
     * @param email Candidate's email.
     * @return Status message.
     */
    String acknowledgeCandidate(String jobPrefix, String email);

    /**
     * Sends reconfirmation mail to a candidate.
     *
     * @param jobPrefix Job identifier.
     * @param email Candidate's email.
     */
    void sendReconfirmationMail(String jobPrefix, String email);

    /**
     * Sends a rejection mail to the candidate.
     *
     * @param jobPrefix Job identifier.
     * @param email Candidate's email.
     */
    void sendRejectionMail(String jobPrefix, String email);

    /**
     * Updates a candidate's job application using job prefix and email.
     *
     * @param dto DTO containing updated job application details.
     */
    void updateJobApplicationByJobPrefixAndEmail(JobApplicationForCandidateDTO dto);

    /**
     * Updates the written test status for aptitude and programming tests.
     *
     * @param jobPrefix Job identifier.
     * @param email Candidate's email.
     * @param isAptitudePassed Aptitude test pass flag.
     * @param isProgrammingPassed Programming test pass flag.
     */
    void updateWrittenTestStatus(String jobPrefix, String email, boolean isAptitudePassed, boolean isProgrammingPassed);

    /**
     * Sends a failure mail to the candidate.
     *
     * @param jobPrefix Job identifier.
     * @param email Candidate's email.
     */
    void sendFailureMail(String jobPrefix, String email);

    /**
     * Sends a success mail to the candidate.
     *
     * @param jobPrefix Job identifier.
     * @param email Candidate's email.
     */
    void sendSuccessMail(String jobPrefix, String email);

	JobApplicationForCandidate scheduleInterview(String jobPrefix, String email);

    /**
     * Sends an exam link to the candidate and updates examLinkStatus.
     *
     * @param jobPrefix Job identifier.
     * @param email Candidate's email.
     * @param dateTime Combined date-time string (e.g. "2025-03-15T14:30").
     */
    void sendExamLink(String jobPrefix, String email, String dateTime);

}
