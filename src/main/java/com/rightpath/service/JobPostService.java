package com.rightpath.service;

import java.util.List;

import com.rightpath.dto.JobPostDTO;
import com.rightpath.entity.JobPost;

/**
 * Service interface for managing job posts and application operations.
 */
public interface JobPostService {

    /**
     * Creates a new job post using the provided data transfer object.
     *
     * @param dto The job post data.
     * @return The created JobPost entity.
     * 
     * // Log Example: log.info("Creating job post with title: {}", dto.getTitle());
     */
    public JobPost createJobPost(JobPostDTO dto);

    /**
     * Converts a JobPost entity to its DTO representation.
     *
     * @param post The JobPost entity.
     * @return The JobPostDTO.
     * 
     * // Log Example: log.debug("Converting JobPost to DTO for jobPrefix: {}", post.getJobPrefix());
     */
    public JobPostDTO convertToDTO(JobPost post);

    /**
     * Retrieves all job posts from the system.
     *
     * @return A list of JobPostDTOs.
     * 
     * // Log Example: log.info("Fetching all job posts");
     */
    public List<JobPostDTO> getAllJobPosts();

    /**
     * Finds a job post by its unique job prefix.
     *
     * @param jobPrefix The job prefix identifier.
     * @return The JobPostDTO if found.
     * 
     * // Log Example: log.info("Searching for job post with prefix: {}", jobPrefix);
     */
    public JobPostDTO findByJobPrefix(String jobPrefix);

    /**
     * Applies a user to a job by job ID and user email.
     *
     * @param jobId     The ID of the job.
     * @param userEmail The applicant's email.
     * @return A success/failure message.
     * 
     * // Log Example: log.info("User {} applying to job ID: {}", userEmail, jobId);
     */
    public String applyToJob(Long jobId, String userEmail);

    /**
     * Retrieves the total number of applications submitted for a given job.
     *
     * @param jobId The job ID.
     * @return The application count.
     * 
     * // Log Example: log.debug("Counting applications for job ID: {}", jobId);
     */
    public int getApplicationCount(Long jobId);
}
