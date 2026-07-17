package com.rightpath.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;

import com.rightpath.dto.JobPostDTO;
import com.rightpath.entity.JobPost;
import com.rightpath.rbac.PermissionName;
import com.rightpath.service.JobPostService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for managing job postings and applications.
 * 
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Creating and retrieving job postings</li>
 *   <li>Applying to job positions</li>
 *   <li>Tracking application metrics</li>
 *   <li>Real-time job posting updates via WebSocket</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs")
public class JobPostController {

    private final JobPostService jobPostService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public JobPostController(JobPostService jobPostService, 
                           SimpMessagingTemplate messagingTemplate) {
        this.jobPostService = jobPostService;
        this.messagingTemplate = messagingTemplate;
        log.info("JobPostController initialized with required dependencies");
    }

    /**
     * Creates a new job posting (Admin only).
     * 
     * @param dto Job posting details including:
     *            - Title
     *            - Description
     *            - Requirements
     *            - Other relevant fields
     * @return The created job posting with HTTP 200 status
     */
    @PostMapping("/post")
    @PreAuthorize("hasAuthority('JOB_POST_CREATE')")
    public ResponseEntity<JobPost> createJobPost(@RequestBody JobPostDTO dto) {
        log.info("Received request to create new job posting");
        JobPost created = jobPostService.createJobPost(dto);
        log.debug("Successfully created job posting with ID: {}", created.getId());

        // Notify subscribers via WebSocket
        messagingTemplate.convertAndSend("/topic/jobPosts", created);
        log.info("Broadcasted new job posting to WebSocket subscribers");

        return ResponseEntity.ok(created);
    }

    /**
     * Retrieves job postings, newest first (descending).
     *
     * <p>Pagination is optional: pass {@code page} and/or {@code size} to get a
     * paginated response (with total counts, etc.); omit both to get the full
     * list. Results are always sorted newest first.</p>
     *
     * @param page zero-based page index (optional; defaults to 0 when only size given)
     * @param size page size (optional; defaults to 10 when only page given)
     * @return all postings (list) when no paging params, else a page of postings
     */
    @GetMapping("/getPost")
    @PreAuthorize("hasAuthority('JOB_POST_READ')")
    public ResponseEntity<?> getAllJobs(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        if (page == null && size == null) {
            log.info("Received request for all job postings (no pagination)");
            return ResponseEntity.ok(jobPostService.getAllJobPosts());
        }

        int pageIndex = page != null ? page : 0;
        int pageSize = size != null ? size : 10;
        if (pageIndex < 0 || pageSize < 1) {
            throw new IllegalArgumentException("page must be >= 0 and size must be >= 1");
        }
        log.info("Received request for job postings page={} size={}", pageIndex, pageSize);
        return ResponseEntity.ok(jobPostService.getJobPosts(pageIndex, pageSize));
    }

    /**
     * Submits a job application for a candidate.
     * 
     * @param jobId ID of the job to apply for
     * @param userEmail Email of the applicant
     * @return Application result message with:
     *         - HTTP 200 if successful
     *         - HTTP 404 if job not found
     *         - HTTP 500 for server errors
     */
    @PostMapping("/apply/{jobId}")
    @PreAuthorize("hasAuthority('JOB_APPLY')")
    public ResponseEntity<String> applyToJob(
            @PathVariable Long jobId, 
            @RequestParam String userEmail) {
        log.info("Received job application request from {} for job ID {}", userEmail, jobId);
        try {
            String result = jobPostService.applyToJob(jobId, userEmail);
            log.debug("Application result: {}", result);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid application attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * Retrieves the number of applications for a specific job.
     * 
     * @param jobId ID of the job to query
     * @return Application count with:
     *         - HTTP 200 if successful
     *         - HTTP 404 if job not found
     *         - HTTP 500 for server errors
     */
    @GetMapping("/applications/count/{jobId}")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<Integer> getApplicationCount(@PathVariable Long jobId) {
        log.info("Received request for application count for job ID {}", jobId);
        try {
            int count = jobPostService.getApplicationCount(jobId);
            log.debug("Job ID {} has {} applications", jobId, count);
            return ResponseEntity.ok(count);
        } catch (IllegalArgumentException e) {
            log.warn("Job not found: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(0);
        }
    }
}