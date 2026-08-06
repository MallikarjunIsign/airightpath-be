package com.rightpath.service;

import java.util.List;

import com.rightpath.dto.JobListingResponse;
import com.rightpath.dto.JobPostDTO;
import com.rightpath.dto.JobPostSearchRequest;
import com.rightpath.dto.JobStatusCountsDTO;
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
     * Retrieves all job posts, newest first (by id descending).
     *
     * @return A list of JobPostDTOs in descending order.
     */
    public List<JobPostDTO> getAllJobPosts();

    /**
     * Retrieves a filtered, sorted page of job posts together with the size of
     * each status bucket for the same search / job-type filter.
     *
     * <p>Defaults applied to an empty request: page 0, size 20 (capped at 100),
     * sort {@code applicationDeadline,asc} and status {@code ACTIVE}. The sort is
     * always given {@code id} as a final tiebreaker so consecutive pages cannot
     * overlap or drop rows.</p>
     *
     * @param request raw query parameters; defaulting and validation happen here.
     * @return A page of JobPostDTOs plus status counts.
     * @throws IllegalArgumentException if page/size/sort/status are unusable.
     */
    public JobListingResponse searchJobPosts(JobPostSearchRequest request);

    /**
     * Counts postings per status bucket for a search / job-type filter, across all
     * pages.
     *
     * @param search  optional free-text term; blank means no text filtering.
     * @param jobType optional job type; blank means no type filtering.
     * @return counts for all/active/expired.
     */
    public JobStatusCountsDTO getStatusCounts(String search, String jobType);

    /**
     * Lists the job types in use, for populating a filter dropdown.
     *
     * <p>Values that differ only by case or separators are collapsed into a single
     * entry (matching how {@code jobType} filtering compares values), represented
     * by their most commonly stored spelling.</p>
     *
     * @return distinct job types sorted alphabetically, ignoring case.
     */
    public List<String> getDistinctJobTypes();

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
