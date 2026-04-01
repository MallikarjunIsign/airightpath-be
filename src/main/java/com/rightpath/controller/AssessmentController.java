package com.rightpath.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightpath.dto.AssessmentUploadDto;
import com.rightpath.dto.AssignAssessmentBlobDto;
import com.rightpath.dto.AssignAssessmentDto;
import com.rightpath.entity.Assessment;
import com.rightpath.entity.Result;
import com.rightpath.repository.AssessmentRepository;
import com.rightpath.repository.ResultRepository;
import com.rightpath.service.AssessmentService;

import jakarta.persistence.EntityNotFoundException;

/**
 * Controller class to handle endpoints related to Assessments.
 */
@RestController
@RequestMapping("/api")
public class AssessmentController {
	@Autowired
	private final AssessmentService assessmentService;
	@Autowired
	private AssessmentRepository assessmentRepository;
	

	@Autowired
	private ResultRepository resultRepository;

	/**
	 * Constructor-based dependency injection for AssessmentService.
	 *
	 * @param assessmentService AssessmentService instance
	 */
	 public AssessmentController(AssessmentService assessmentService) {
			this.assessmentService = assessmentService;
		}

	/**
	 * Endpoint to upload an assessment. Accessible by users with ROLE_ADMIN.
	 *
	 * @param dto AssessmentUploadDto containing assessment details
	 * @return ResponseEntity with status and message
	 */
	@PostMapping("/upload")
	@PreAuthorize("hasAuthority('ASSESSMENT_UPLOAD')")
	public ResponseEntity<Map<String, String>> uploadAssessment(@ModelAttribute AssessmentUploadDto dto) {
		Map<String, String> response = new HashMap<>();
		try {
			assessmentService.uploadAssessment(dto);
			response.put("status", "success");
			response.put("message", "Assessment uploaded successfully.");
			return ResponseEntity.ok(response); // Return JSON response
		} catch (Exception e) {
			response.put("status", "error");
			response.put("message", "Error uploading assessment: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Endpoint to assign an assessment. Accessible by users with ROLE_ADMIN.
	 *
	 * @param dto AssignAssessmentDto containing assignment details
	 * @return ResponseEntity with status and message
	 */
	@PostMapping("/assign")
	@PreAuthorize("hasAuthority('ASSESSMENT_ASSIGN')")
    public ResponseEntity<Map<String, String>> assignAssessment(
        @RequestPart("candidateEmails") String candidateEmails,
        @RequestPart("startTime") String startTime,
        @RequestPart("deadline") String deadline,
        @RequestPart(value = "aptitudeQuestionPaper", required = false) MultipartFile aptitudeQuestionPaper,
        @RequestPart(value = "codingQuestionPaper", required = false) MultipartFile codingQuestionPaper,
        @RequestPart(value = "aptitudeAnswerKey", required = false) MultipartFile aptitudeAnswerKey,
        @RequestPart("uploadedBy") String uploadedBy,
        @RequestPart("jobPrefix") String jobPrefix
    ) {
        Map<String, String> response = new HashMap<>();
        
        try {
            // Parse datetime strings
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime parsedStartTime = LocalDateTime.parse(startTime, formatter);
            LocalDateTime parsedDeadline = LocalDateTime.parse(deadline, formatter);

            // Validate time range
            if (parsedStartTime.isAfter(parsedDeadline)) {
                response.put("status", "error");
                response.put("message", "Start time must be before deadline");
                return ResponseEntity.badRequest().body(response);
            }

            // Build DTO
            AssignAssessmentDto dto = new AssignAssessmentDto();
            dto.setCandidateEmails(List.of(candidateEmails.split(",")));
            dto.setStartTime(parsedStartTime);
            dto.setDeadline(parsedDeadline);
            dto.setAptitudeQuestionPaper(aptitudeQuestionPaper);
            dto.setCodingQuestionPaper(codingQuestionPaper);
            dto.setAptitudeAnswerKey(aptitudeAnswerKey);
            dto.setUploadedBy(uploadedBy);
            dto.setJobPrefix(jobPrefix);

            // Call service
            assessmentService.assignAssessment(dto, jobPrefix);
            
            response.put("status", "success");
            response.put("message", "Assessments assigned successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to assign assessments: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }


	/**
	 * Endpoint to get assessments for a candidate by email.
	 *
	 * @param candidateEmail Email of the candidate
	 * @return ResponseEntity with list of assessments or error message
	 */
	@GetMapping("getCandidateAssessments/{candidateEmail}")
	public ResponseEntity<?> getCandidateAssessments(@PathVariable String candidateEmail) {
		try {
			List<Assessment> assessments = assessmentService.getCandidateAssessments(candidateEmail);
			return ResponseEntity.ok(assessments);
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
		}
	}

	/**
	 * Endpoint to fetch details of a specific assessment by ID.
	 *
	 * @param id Assessment ID
	 * @return ResponseEntity with assessment details
	 */
	@GetMapping("fetchAssessment/{id}")
	public ResponseEntity<Map<String, Object>> fetchAssessmentDetails(@PathVariable Long id) {
		Assessment assessment = assessmentService.fetchAssessmentDetails(id);
		Map<String, Object> response = new HashMap<>();
		response.put("assessmentType", assessment.getAssessmentType());
		response.put("questions", assessment.getQuestionPaper()); // Assuming this column contains JSON string
		response.put("jobPrefix", assessment.getJobPrefix());
		return ResponseEntity.ok(response);
	}

	
	

	/**
	 * Endpoint to submit a result for an assessment.
	 *
	 * @param candidateEmail Candidate's email
	 * @param assessmentType Type of assessment
	 * @param score          Score obtained
	 * @return ResponseEntity with status and message
	 */
	@PostMapping("/result")
	@PreAuthorize("hasAuthority('ASSESSMENT_RESULT_SUBMIT')")
	public ResponseEntity<Map<String, Object>> resultAssessment(
	    @RequestParam String candidateEmail,
	    @RequestParam String assessmentType,
	    @RequestParam Double score,
	    @RequestBody String resultsJson,
	    @RequestParam String jobPrefix // ✅ FIXED: Accept jobPrefix
	) {
	    String message = assessmentService.resultAssessment(candidateEmail, assessmentType, score, resultsJson, jobPrefix);

	    Map<String, Object> response = new HashMap<>();
	    response.put("message", message);
	    response.put("status", "success");
	    return ResponseEntity.ok(response);
	}


	/**
	 * Endpoint to submit an assessment by ID. Accessible by users with ROLE_USER.
	 *
	 * @param id Assessment ID
	 * @return ResponseEntity with status and message
	 */
	@PostMapping("/submit/{id}")
	@PreAuthorize("hasAuthority('ASSESSMENT_SUBMIT')")
	public ResponseEntity<Map<String, Object>> submitAssessment(@PathVariable Long id) {
		try {
			// Call service method to submit assessment
			String message = assessmentService.submitAssessment(id);

			// Prepare success response
			Map<String, Object> response = new HashMap<>();
			response.put("message", message);
			response.put("status", "success");
			return ResponseEntity.ok(response);
		} catch (EntityNotFoundException e) {
			// Handle not found error
			Map<String, Object> response = new HashMap<>();
			response.put("message", e.getMessage());
			response.put("status", "error");
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
		} catch (Exception e) {
			// Handle generic errors
			Map<String, Object> response = new HashMap<>();
			response.put("message", "An unexpected error occurred.");
			response.put("status", "error");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Endpoint to fetch assessments, optionally filtered by candidate email or
	 * fetch all.
	 *
	 * @param candidateEmail Optional candidate email
	 * @param fetchAll       Flag to fetch all assessments
	 * @return ResponseEntity with list of assessments
	 */
	@GetMapping("/getAssessments")
	public ResponseEntity<List<Assessment>> getAssessments(@RequestParam(required = false) String candidateEmail,
			@RequestParam(required = false, defaultValue = "false") boolean fetchAll) {
		return ResponseEntity.ok(assessmentService.getAssessments(candidateEmail, fetchAll));
	}

	/**
	 * Scheduled task to expire assessments periodically. Runs every hour as per the
	 * cron expression.
	 */
	@Scheduled(cron = "0 0 * * * *") // Runs every hour
	public void scheduleExpireAssessments() {
		assessmentService.expireAssessments();

	}

	/**
	 * Retrieves results based on the candidate's email.
	 *
	 * @param email The candidate's email address.
	 * @return A list of Result entities.
	 */
//	@GetMapping("/get-results/{email}")
//	public ResponseEntity<List<Result>> getResultsByEmail(@PathVariable("email") String email) {
//		try {
//			List<Result> results = assessmentService.getResultsByEmail(email);
//
//			return ResponseEntity.ok(results);
//		} catch (IllegalArgumentException e) { 
//			return ResponseEntity.badRequest().body(null);
//		}
//	}
	
	@GetMapping("/get-results")
	public ResponseEntity<List<Result>> getResultsByEmailAndJobPrefix(
	    @RequestParam String email,
	    @RequestParam String jobPrefix
	) {
	    try {
	        List<Result> results = assessmentService.getResultsByEmailAndJobPrefix(email, jobPrefix);
	        return ResponseEntity.ok(results);
	    } catch (IllegalArgumentException e) {
	        return ResponseEntity.badRequest().body(null);
	    }
	}

	@GetMapping("/get-results-by-job-prefix")
	@PreAuthorize("hasAuthority('JOB_APPLICATION_READ_ALL')")
	public ResponseEntity<List<Result>> getResultsByJobPrefix(@RequestParam String jobPrefix) {
	    List<Result> results = resultRepository.findByJobPrefix(jobPrefix);
	    return ResponseEntity.ok(results);
	}


	/**
	 * Endpoint to fetch a Result by its ID.
	 *
	 * @param id The ID of the Result entity.
	 * @return The Result entity if found, or a 404 response if not found.
	 */
	@GetMapping("/get-results-by-id/{id}")
	public ResponseEntity<?> getResultById(@PathVariable Long id) {
		Optional<Result> result = assessmentService.getResultById(id);
		if (result.isPresent()) {
			return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
		} else {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No result found for the given ID.");
		}
	}

	@GetMapping("/question-paper")
    public ResponseEntity<String> getQuestionPaper(@RequestParam String email,
                                                   @RequestParam String jobPrefix,
                                                   @RequestParam String assessmentType) {

        String paper = assessmentService.getLatestQuestionPaper(
                email, jobPrefix, assessmentType);

        return new ResponseEntity<>(paper, HttpStatus.OK);
    }
	
	@PostMapping("/markExamAttended")
    public ResponseEntity<?> markExamAttended(@RequestBody Map<String, Object> request) {
        try {
            String candidateEmail = (String) request.get("candidateEmail");
            Long assessmentId = Long.parseLong(request.get("assessmentId").toString());
          //  String jobPrefix = (String) request.get("jobPrefix");
            
            assessmentService.markExamAsAttended(candidateEmail, assessmentId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error marking exam as attended");
        }
    }

	
	
	@PostMapping("/assign-blob")
	@PreAuthorize("hasAuthority('ASSESSMENT_ASSIGN')")
	public ResponseEntity<Map<String, String>> assignAssessmentBlob(
			@RequestPart("candidateEmails") String candidateEmails,
			@RequestPart("startTime") String startTime,
			@RequestPart("deadline") String deadline,
			@RequestPart("file") MultipartFile file,
			@RequestPart("fileName") String fileName,
			@RequestPart("uploadedBy") String uploadedBy,
			@RequestPart("jobPrefix") String jobPrefix,
			@RequestPart("assessmentType") String assessmentType
	) {
		Map<String, String> response = new HashMap<>();
		try {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
			LocalDateTime parsedStartTime = LocalDateTime.parse(startTime, formatter);
			LocalDateTime parsedDeadline = LocalDateTime.parse(deadline, formatter);

			if (parsedStartTime.isAfter(parsedDeadline)) {
				response.put("status", "error");
				response.put("message", "Start time must be before deadline");
				return ResponseEntity.badRequest().body(response);
			}

			AssignAssessmentBlobDto dto = new AssignAssessmentBlobDto();
			dto.setCandidateEmails(List.of(candidateEmails.split(",")));
			dto.setStartTime(parsedStartTime);
			dto.setDeadline(parsedDeadline);
			dto.setFile(file);
			dto.setFileName(fileName);
			dto.setUploadedBy(uploadedBy);
			dto.setJobPrefix(jobPrefix);
			dto.setAssessmentType(assessmentType);

			assessmentService.assignAssessmentToStorage(dto);

			response.put("status", "success");
			response.put("message", "Assessments assigned with storage successfully");
			return ResponseEntity.ok(response);

		} catch (Exception e) {
			response.put("status", "error");
			response.put("message", "Failed to assign assessments: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}
	
	@GetMapping("/assessments/{id}/content")
	public ResponseEntity<?> getAssessmentFileContent(@PathVariable Long id) {
		try {
			Assessment assessment = assessmentRepository.findById(id)
					.orElseThrow(() -> new RuntimeException("Assessment not found with id: " + id));

			String containerName = assessment.getContainerName();
			String fileName = assessment.getFileName();

			String fileContent = assessmentService.downloadFileContentFromStorage(containerName, fileName);

			ObjectMapper objectMapper = new ObjectMapper();
			List<Map<String, Object>> questions = objectMapper.readValue(
					fileContent, new TypeReference<List<Map<String, Object>>>() {}
			);

			return ResponseEntity.ok(questions);

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
								 .body(Map.of("error", "Failed to fetch file content: " + e.getMessage()));
		}
	}

	@GetMapping("/assessments/content/latest")
	public ResponseEntity<?> getLatestAssessmentContent(
	        @RequestParam String jobPrefix,
	        @RequestParam String candidateEmail,
	        @RequestParam String assessmentType) {
	    try {
	        List<Map<String, Object>> questions =
	                assessmentService.getLatestAssessmentContent(jobPrefix, candidateEmail, assessmentType);

	        return ResponseEntity.ok(questions);
	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(Map.of("error", "Failed to fetch assessment content: " + e.getMessage()));
	    }
	}

	


	
	
}