	package com.rightpath.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightpath.dto.BulkMailRequestDTO;
import com.rightpath.dto.JobApplicationForCandidateDTO;
import com.rightpath.entity.JobApplicationForCandidate;
import com.rightpath.repository.JobApplicationForCandidateRepository;
import com.rightpath.rbac.PermissionName;
import com.rightpath.service.JobApplicationForCandidateService;
import com.rightpath.service.impl.EmailServiceImpl;

@RestController
@RequestMapping("/api/job-applications")
public class JobApplicationForCandidateController {

    @Autowired
    private JobApplicationForCandidateService applicationForCandidateService;
    
    @Autowired
    private JobApplicationForCandidateRepository applicationForCandidateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private EmailServiceImpl emailService;

    private static final String WS_TOPIC = "/queue/updates";
    private static final String WS_TYPE = "APPLICATION_UPDATE";

    // Utility to send real-time WebSocket notifications to a specific user
    private void sendWebSocketNotification(String email, String jobPrefix, String status, String message) {
        try {
            messagingTemplate.convertAndSendToUser(
                email, 
                WS_TOPIC,
                Map.of(
                    "type", WS_TYPE,
                    "jobPrefix", jobPrefix,
                    "status", status,
                    "message", message,
                    "timestamp", System.currentTimeMillis()
                )
            );
        } catch (Exception e) {
            System.err.println("WebSocket notification failed: " + e.getMessage());
        }
    }

    // Constructor injection for unit testing
    public JobApplicationForCandidateController(
        JobApplicationForCandidateRepository applicationForCandidateRepository,
        JobApplicationForCandidateService applicationForCandidateService
    ) {
        this.applicationForCandidateRepository = applicationForCandidateRepository;
        this.applicationForCandidateService = applicationForCandidateService;
    }

    /**
     * Submit a new job application with resume and metadata
     */
    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('JOB_APPLY')")
    public ResponseEntity<?> applyJob(
        @RequestPart("jobApplication") String jobApplicationJson,
        @RequestPart("resume") MultipartFile resume) {

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JobApplicationForCandidateDTO dto = objectMapper.readValue(jobApplicationJson, JobApplicationForCandidateDTO.class);
            dto.setResume(resume);
            applicationForCandidateService.applyForJob(dto);

            return ResponseEntity.ok("Job Application Submitted Successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error parsing jobApplication data: " + e.getMessage());
        }
    }

    /**
     * Update existing job application (resume optional)
     */
    @PatchMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('JOB_APPLY')")
    public ResponseEntity<?> updateJobApplication(
        @RequestPart("jobApplication") String jobApplicationJson,
        @RequestPart(value = "resume", required = false) MultipartFile resume) {

        try {
            JobApplicationForCandidateDTO dto = objectMapper.readValue(jobApplicationJson, JobApplicationForCandidateDTO.class);
            if (resume != null && !resume.isEmpty()) {
                dto.setResume(resume);
            }

            applicationForCandidateService.updateJobApplicationByJobPrefixAndEmail(dto);
            return ResponseEntity.ok("Job application updated successfully.");
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body("Invalid JSON data: " + ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Failed to update application: " + ex.getMessage());
        }
    }

    /**
     * Admin: Fetch all applications
     */
    @GetMapping("/getAllApplications")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<List<JobApplicationForCandidateDTO>> getAllApplications() {
        return ResponseEntity.ok(applicationForCandidateService.getAllApplications());
    }

    /**
     * Candidate: Get all applications for a given email
     */
    @GetMapping("/{email}")
    @PreAuthorize("hasAuthority('JOB_APPLY')")
    public ResponseEntity<List<JobApplicationForCandidateDTO>> getApplicationsByEmail(@PathVariable String email) {
        return ResponseEntity.ok(applicationForCandidateService.getApplicationsByEmail(email));
    }

    /**
     * Admin: Get applications by jobPrefix
     */
    @GetMapping("/byJobPrefix/{jobPrefix}")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<List<JobApplicationForCandidateDTO>> getApplicationsByJobPrefix(@PathVariable String jobPrefix) {
        return ResponseEntity.ok(applicationForCandidateService.getApplicationsByJobPrefix(jobPrefix));
    }

    /**
     * Filter candidates by jobPostId
     */
    @GetMapping("/filter/{jobPostId}")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<List<JobApplicationForCandidateDTO>> filterCandidates(@PathVariable Long jobPostId) {
        return ResponseEntity.ok(applicationForCandidateService.filterCandidates(jobPostId));
    }

    /**
     * Filter candidates by jobPrefix
     */
    @GetMapping("/filterByPrefix/{jobPrefix}")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<List<JobApplicationForCandidateDTO>> filterCandidatesByPrefix(@PathVariable String jobPrefix) {
        return ResponseEntity.ok(applicationForCandidateService.filterCandidatesByPrefix(jobPrefix));
    }

    /**
     * Admin: Get all applicants for a job
     */
    @GetMapping("/applicants/{jobPostId}")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<List<JobApplicationForCandidateDTO>> getApplicantsByJobPostId(@PathVariable Long jobPostId) {
        return ResponseEntity.ok(applicationForCandidateService.getApplicantsByJobPostId(jobPostId));
    }

    /**
     * Get specific application by jobPrefix and candidate email
     */
    @GetMapping("/byJobPrefixAndEmail/{jobPrefix}/{email}")
    @PreAuthorize("hasAuthority('JOB_APPLY')")
    public ResponseEntity<JobApplicationForCandidateDTO> getApplicationByJobPrefixAndEmail(
        @PathVariable String jobPrefix,
        @PathVariable String email) {

        List<JobApplicationForCandidate> applications = applicationForCandidateRepository.findByJobPrefixAndEmail(jobPrefix, email);

        if (applications.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok(convertToDTO(applications.get(0)));
    }

    private JobApplicationForCandidateDTO convertToDTO(JobApplicationForCandidate entity) {
        JobApplicationForCandidateDTO dto = new JobApplicationForCandidateDTO();

        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setExperience(entity.getExperience());
        dto.setAddress(entity.getAddress());
        dto.setJobRole(entity.getJobRole());
        dto.setResumeFileName(entity.getResumeFileName());
        dto.setUserEmail(entity.getUser().getEmail());
        dto.setJobPrefix(entity.getJobPost().getJobPrefix());
        dto.setEmail(entity.getUser().getEmail());
        dto.setStatus(entity.getStatus().toString());
        dto.setMobileNumber(entity.getMobileNumber());
        dto.setConfirmationStatus(entity.getConfirmationStatus());
        dto.setAcknowledgedStatus(entity.getAcknowledgedStatus());
        dto.setReconfirmationStatus(entity.getReconfirmationStatus());
        dto.setExamLinkStatus(entity.getExamLinkStatus());
        dto.setExamCompletedStatus(entity.getExamCompletedStatus());
        dto.setRejectionStatus(entity.getRejectionStatus());

        return dto;
    }

    /**
     * ATS Screening - Shortlisted Candidates
     */
    @GetMapping("/ats-screening/{jobPrefix}")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<List<JobApplicationForCandidateDTO>> getShortlistedCandidates(@PathVariable String jobPrefix) {
        return ResponseEntity.ok(applicationForCandidateService.getShortlistedCandidatesByPrefix(jobPrefix));
    }

    /**
     * ATS Screening - Rejected Candidates
     */
    @GetMapping("/ats-rejected/{jobPrefix}")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<List<JobApplicationForCandidateDTO>> getRejectedCandidates(@PathVariable String jobPrefix) {
        return ResponseEntity.ok(applicationForCandidateService.getRejectedCandidatesByPrefix(jobPrefix));
    }

    /**
     * Send acknowledgement mail and update status (bulk support)
     */
    @PostMapping("/send-ack-mail")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<?> sendAckMail(@RequestBody BulkMailRequestDTO request) {
        List<String> errors = new ArrayList<>();
        String jobPrefix = request.getJobPrefix();

        if (request.getEmails() == null || request.getEmails().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "At least one email is required"));
        }

        if (jobPrefix == null || jobPrefix.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Job prefix is required"));
        }

        // dateTime is required for ack mail (email contains exam schedule)
        if (request.getDateTime() == null || request.getDateTime().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Date & Time is required for acknowledgement mail"));
        }

        // Parse combined dateTime (e.g. "2025-03-15T14:30") into separate date and time
        String date;
        String time;
        try {
            LocalDateTime ldt = LocalDateTime.parse(request.getDateTime());
            date = ldt.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            time = ldt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid dateTime format. Expected: yyyy-MM-ddTHH:mm"));
        }

        boolean hasValidationError = false;
        for (String email : request.getEmails()) {
            try {
                applicationForCandidateService.sendAcknowledgementMailAndUpdateStatus(jobPrefix, email, date, time);
                sendWebSocketNotification(email, jobPrefix, "CONFIRMATION_SENT", "Confirmation email sent to candidate");
            } catch (IllegalStateException e) {
                errors.add(email + ": " + e.getMessage());
                hasValidationError = true;
            } catch (Exception e) {
                errors.add(email + ": " + e.getMessage());
            }
        }

        if (errors.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "Confirmation mail sent successfully."));
        }
        HttpStatus status = hasValidationError ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
            .body(Map.of("message", "Some emails failed", "errors", errors));
    }

    /**
     * Candidate Acknowledges confirmation
     */
    @GetMapping("/acknowledge")
    @PreAuthorize("hasAuthority('JOB_APPLY')")
    public ResponseEntity<Map<String, String>> acknowledgeMail(
        @RequestParam String jobPrefix,
        @RequestParam String email) {

        try {
            String result = applicationForCandidateService.acknowledgeCandidate(jobPrefix, email);
            sendWebSocketNotification(email, jobPrefix, "ACKNOWLEDGED_BACK", "Candidate acknowledged the confirmation");
            return ResponseEntity.ok(Map.of("status", "success", "message", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                               .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Send re-confirmation mail (bulk support)
     */
    @PostMapping("/send-reconfirmation-mail")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<?> sendReconfirmationMail(@RequestBody BulkMailRequestDTO request) {
        List<String> errors = new ArrayList<>();
        String jobPrefix = request.getJobPrefix();

        boolean hasValidationError = false;
        for (String email : request.getEmails()) {
            try {
                applicationForCandidateService.sendReconfirmationMail(jobPrefix, email);
                sendWebSocketNotification(email, jobPrefix, "RECONFIRMATION_SENT", "Reconfirmation mail sent with exam details");
            } catch (IllegalStateException e) {
                errors.add(email + ": " + e.getMessage());
                hasValidationError = true;
            } catch (Exception e) {
                errors.add(email + ": " + e.getMessage());
            }
        }

        if (errors.isEmpty()) {
            return ResponseEntity.ok("Reconfirmation mail sent successfully.");
        }
        HttpStatus status = hasValidationError ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
            .body(Map.of("message", "Some emails failed", "errors", errors));
    }

    /**
     * Send rejection mail (bulk support)
     */
    @PostMapping("/send-rejection-mail")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<?> sendRejectionMail(@RequestBody BulkMailRequestDTO request) {
        List<String> errors = new ArrayList<>();
        String jobPrefix = request.getJobPrefix();

        boolean hasValidationError = false;
        for (String email : request.getEmails()) {
            try {
                applicationForCandidateService.sendRejectionMail(jobPrefix, email);
                sendWebSocketNotification(email, jobPrefix, "REJECTED", "Application rejected with notification sent");
            } catch (IllegalStateException e) {
                errors.add(email + ": " + e.getMessage());
                hasValidationError = true;
            } catch (Exception e) {
                errors.add(email + ": " + e.getMessage());
            }
        }

        if (errors.isEmpty()) {
            return ResponseEntity.ok("Rejection mail sent successfully.");
        }
        HttpStatus status = hasValidationError ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
            .body(Map.of("message", "Some emails failed", "errors", errors));
    }

    /**
     * Update written test result (aptitude/programming)
     */
    @PostMapping("/update-written-test-status")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<String> updateWrittenTestStatus(
        @RequestParam String jobPrefix,
        @RequestParam String email,
        @RequestParam boolean isAptitudePassed,
        @RequestParam boolean isProgrammingPassed) {

        try {
            applicationForCandidateService.updateWrittenTestStatus(jobPrefix, email, isAptitudePassed, isProgrammingPassed);
            return ResponseEntity.ok("Written Test Status updated successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    /**
     * Send success mail after evaluation (bulk support)
     */
    @PostMapping("/send-success-mail")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<?> sendSuccessMail(@RequestBody BulkMailRequestDTO request) {
        List<String> errors = new ArrayList<>();
        String jobPrefix = request.getJobPrefix();

        boolean hasValidationError = false;
        for (String email : request.getEmails()) {
            try {
                applicationForCandidateService.sendSuccessMail(jobPrefix, email);
                sendWebSocketNotification(email, jobPrefix, "SELECTED", "Candidate selected successfully");
            } catch (IllegalStateException e) {
                errors.add(email + ": " + e.getMessage());
                hasValidationError = true;
            } catch (Exception e) {
                errors.add(email + ": " + e.getMessage());
            }
        }

        if (errors.isEmpty()) {
            return ResponseEntity.ok("Success email sent successfully.");
        }
        HttpStatus status = hasValidationError ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
            .body(Map.of("message", "Some emails failed", "errors", errors));
    }

    /**
     * Send failure mail after evaluation (bulk support)
     */
    @PostMapping("/send-failure-mail")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<?> sendFailureMail(@RequestBody BulkMailRequestDTO request) {
        List<String> errors = new ArrayList<>();
        String jobPrefix = request.getJobPrefix();

        boolean hasValidationError = false;
        for (String email : request.getEmails()) {
            try {
                applicationForCandidateService.sendFailureMail(jobPrefix, email);
                sendWebSocketNotification(email, jobPrefix, "REJECTED", "Candidate rejected after evaluation");
            } catch (IllegalStateException e) {
                errors.add(email + ": " + e.getMessage());
                hasValidationError = true;
            } catch (Exception e) {
                errors.add(email + ": " + e.getMessage());
            }
        }

        if (errors.isEmpty()) {
            return ResponseEntity.ok("Failure email sent successfully.");
        }
        HttpStatus status = hasValidationError ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
            .body(Map.of("message", "Some emails failed", "errors", errors));
    }
    
    
    /**
     * Send exam link to candidates (bulk support)
     */
    @PostMapping("/send-exam-link")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<?> sendExamLink(@RequestBody BulkMailRequestDTO request) {
        List<String> errors = new ArrayList<>();
        String jobPrefix = request.getJobPrefix();

        boolean hasValidationError = false;
        for (String email : request.getEmails()) {
            try {
                applicationForCandidateService.sendExamLink(jobPrefix, email, request.getDateTime());
                sendWebSocketNotification(email, jobPrefix, "EXAM_SENT", "Exam link sent to candidate");
            } catch (IllegalStateException e) {
                errors.add(email + ": " + e.getMessage());
                hasValidationError = true;
            } catch (Exception e) {
                errors.add(email + ": " + e.getMessage());
            }
        }

        if (errors.isEmpty()) {
            return ResponseEntity.ok("Exam link sent successfully.");
        }
        HttpStatus status = hasValidationError ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
            .body(Map.of("message", "Some emails failed", "errors", errors));
    }

    @PostMapping("/schedule-interview")
    @PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
    public ResponseEntity<?> scheduleInterview(
            @RequestParam String jobPrefix,
            @RequestParam String email) {

        try {
            JobApplicationForCandidate updated = applicationForCandidateService.scheduleInterview(jobPrefix, email);
            return ResponseEntity.ok(new JobApplicationForCandidateDTO(updated));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

}