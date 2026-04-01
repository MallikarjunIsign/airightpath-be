package com.rightpath.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightpath.dto.CodeErrorInfo;
import com.rightpath.dto.CodeSubmissionRequestDTO;
import com.rightpath.dto.CodeSubmissionResponseDTO;
import com.rightpath.dto.TestCaseDTO;
import com.rightpath.entity.CodeSubmission;
import com.rightpath.entity.TestResultEntity;
import com.rightpath.rbac.PermissionName;
import com.rightpath.repository.CodeSubmissionRepository;
import com.rightpath.service.impl.CompilerServiceDiffL;

import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for code compilation and execution services.
 * 
 * <p>
 * Provides endpoints for:
 * <ul>
 * <li>Code execution with test cases</li>
 * <li>Retrieval of submission history</li>
 * <li>Filtering submissions by various criteria</li>
 * </ul>
 * 
 * <p>
 * Supports multiple programming languages and handles both custom input and
 * predefined test cases.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/compiler")
public class CompilerController {

	@Autowired
	private CompilerServiceDiffL compilerService;

	@Autowired
	private CodeSubmissionRepository codeSubmissionRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Executes submitted code with optional test cases or custom input.
	 * 
	 * @param dto Code submission request containing: - Language (Java, Python,
	 *            etc.) - Code script - User email - Test cases (optional) - Custom
	 *            input (optional)
	 * @return Response with execution results including: - Output for each test
	 *         case - Error information if any - Pass/fail status
	 */
	@PostMapping("/run")
	@PreAuthorize("hasAuthority('COMPILER_RUN')")
	public ResponseEntity<CodeSubmissionResponseDTO> runCode(@RequestBody CodeSubmissionRequestDTO dto) {
		log.info("Received code execution request from user: {}", dto.getUserEmail());
		log.debug("Execution details - Language: {}, Question ID: {}", dto.getLanguage(), dto.getQuestionId());

		CodeSubmission entity = new CodeSubmission();
		entity.setLanguage(dto.getLanguage());
		entity.setScript(dto.getScript());
		entity.setUserEmail(dto.getUserEmail());
		entity.setCreatedAt(dto.getCreatedAt());
		entity.setQuestionId(dto.getQuestionId());
		entity.setJobPrefix(dto.getJobPrefix());

		CodeSubmissionResponseDTO response = new CodeSubmissionResponseDTO();
		response.setLanguage(dto.getLanguage());
		response.setScript(dto.getScript());
		response.setUserEmail(dto.getUserEmail());
		response.setCreatedAt(dto.getCreatedAt());

		try {
			// Case 1: Custom input provided
			if ((dto.getTestCases() == null || dto.getTestCases().isEmpty()) && dto.getCustomInput() != null
					&& !dto.getCustomInput().trim().isEmpty()) {

				log.debug("Processing custom input execution");
				return processCustomInputExecution(entity, dto, response);
			}

			// Case 2: Test cases provided
			if (dto.getTestCases() != null && !dto.getTestCases().isEmpty()) {
				log.debug("Processing test case execution with {} test cases", dto.getTestCases().size());
				return processTestCasesExecution(entity, dto, response);
			}

			// Case 3: No test cases or custom input - run with empty input
			log.debug("Processing default execution with empty input");
			return processDefaultExecution(entity, dto, response);

		} catch (Exception e) {
			log.error("Error during code execution: {}", e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}

	private ResponseEntity<CodeSubmissionResponseDTO> processCustomInputExecution(CodeSubmission entity,
			CodeSubmissionRequestDTO dto, CodeSubmissionResponseDTO response) {

		TestResultEntity result = compilerService.executeSingleInput(entity, dto.getCustomInput());
		result.setSubmission(entity);
		result.setPassed(false);

		entity.setTestResults(List.of(result));
		codeSubmissionRepository.save(entity);

		TestCaseDTO customCase = createTestCaseDTO(result, dto.getCustomInput(), null);
		response.setTestResults(List.of(customCase));

		log.debug("Custom input execution completed successfully");
		return ResponseEntity.ok(response);
	}

	private ResponseEntity<CodeSubmissionResponseDTO> processTestCasesExecution(CodeSubmission entity,
			CodeSubmissionRequestDTO dto, CodeSubmissionResponseDTO response) {

		List<TestResultEntity> testEntities = dto.getTestCases().stream().map(tc -> createTestResultEntity(tc, entity))
				.collect(Collectors.toList());

		entity.setTestResults(testEntities);
		List<TestResultEntity> results = compilerService.executeCode(entity);

		evaluateTestResults(testEntities, results);
		codeSubmissionRepository.save(entity);

		response.setTestResults(mapToTestCaseDTOs(results, dto.getLanguage()));
		log.debug("Test case execution completed with {} results", results.size());

		return ResponseEntity.ok(response);
	}

	private ResponseEntity<CodeSubmissionResponseDTO> processDefaultExecution(CodeSubmission entity,
			CodeSubmissionRequestDTO dto, CodeSubmissionResponseDTO response) {

		TestResultEntity defaultResult = compilerService.executeSingleInput(entity, "");
		defaultResult.setSubmission(entity);
		defaultResult.setInput("");
		defaultResult.setExpectedOutput(defaultResult.getActualOutput());
		defaultResult.setPassed(true);

		entity.setTestResults(List.of(defaultResult));
		codeSubmissionRepository.save(entity);

		TestCaseDTO defaultCase = createTestCaseDTO(defaultResult, "", defaultResult.getExpectedOutput());
		response.setTestResults(List.of(defaultCase));

		log.debug("Default execution completed successfully");
		return ResponseEntity.ok(response);
	}

	@PostMapping("/saveUnattemptedSubmissions")
	@PreAuthorize("hasAuthority('COMPILER_RUN')")
	public ResponseEntity<?> saveUnattemptedSubmissions(@RequestBody List<CodeSubmissionRequestDTO> unattemptedDtos) {
		try {
			for (CodeSubmissionRequestDTO dto : unattemptedDtos) {
				boolean exists = codeSubmissionRepository.findTopByUserEmailAndJobPrefixAndQuestionIdOrderByIdDesc(
						dto.getUserEmail(), dto.getJobPrefix(), dto.getQuestionId()).isPresent();

				// ❌ Skip if already exists
				if (exists)
					continue;

				// ✅ Create new record
				CodeSubmission entity = new CodeSubmission();
				entity.setLanguage(dto.getLanguage());
				entity.setScript(dto.getScript());
				entity.setUserEmail(dto.getUserEmail());
				entity.setJobPrefix(dto.getJobPrefix());
				entity.setQuestionId(dto.getQuestionId());
				entity.setCreatedAt(LocalDateTime.now());
				entity.setPassed(false);
				entity.setAttempted(true);
				entity.setTestResults(List.of());

				codeSubmissionRepository.save(entity);
			}

			return ResponseEntity.ok("Unattempted submissions saved successfully (existing skipped).");
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body("Error saving unattempted submissions");
		}
	}

	/**
	 * Retrieves submission history for a user filtered by language.
	 * 
	 * @param userEmail User's email address
	 * @param language  Programming language to filter by
	 * @return List of submissions with test results
	 */
	@GetMapping("/results/{userEmail}/{language}")
	@PreAuthorize("hasAuthority('COMPILER_RESULTS_READ')")
	public ResponseEntity<List<CodeSubmissionResponseDTO>> getResultsByUserAndLanguage(@PathVariable String userEmail,
			@PathVariable String language) {

		log.info("Fetching submissions for user: {} in language: {}", userEmail, language);
		List<CodeSubmission> submissions = compilerService.getSubmissionsByUserAndLanguage(userEmail, language);

		List<CodeSubmissionResponseDTO> response = mapSubmissionsToDTOs(submissions);
		log.debug("Found {} submissions", response.size());

		return ResponseEntity.ok(response);
	}

	/**
	 * Retrieves all submissions for a user.
	 * 
	 * @param userEmail User's email address
	 * @return List of all user submissions
	 */
//    @GetMapping("/results/{userEmail}")
//    public ResponseEntity<List<CodeSubmissionResponseDTO>> getResultsByUserEmail(@PathVariable String userEmail) {
//        log.info("Fetching all submissions for user: {}", userEmail);
//        List<CodeSubmission> submissions = compilerService.getSubmissionsByUserEmail(userEmail);
//        
//        List<CodeSubmissionResponseDTO> response = mapSubmissionsToDTOs(submissions);
//        log.debug("Found {} total submissions", response.size());
//        
//        return ResponseEntity.ok(response);
//    }

	@GetMapping("/results")
	@PreAuthorize("hasAuthority('COMPILER_RESULTS_READ')")
	public ResponseEntity<List<CodeSubmissionResponseDTO>> getResultsByEmailAndJobPrefix(@RequestParam String userEmail,
			@RequestParam String jobPrefix) {

		log.info("Fetching latest submissions for user: {} and jobPrefix: {}", userEmail, jobPrefix);

		// Fetch only latest per question
		List<CodeSubmission> submissions = compilerService.getLatestSubmissionsByUserEmailAndJobPrefix(userEmail,
				jobPrefix);

		List<CodeSubmissionResponseDTO> response = mapSubmissionsToDTOs(submissions);

		log.debug("Found {} latest submissions for user {} and jobPrefix {}", response.size(), userEmail, jobPrefix);
		return ResponseEntity.ok(response);
	}

	/**
	 * Retrieves submissions for a specific question by a user.
	 * 
	 * @param userEmail  User's email address
	 * @param questionId ID of the question
	 * @return List of submissions for the specified question
	 */
	@GetMapping("/results/{userEmail}/question/{questionId}")
	@PreAuthorize("hasAuthority('COMPILER_RESULTS_READ')")
	public ResponseEntity<List<CodeSubmissionResponseDTO>> getResultsByUserAndQuestion(@PathVariable String userEmail,
			@PathVariable String questionId) {

		log.info("Fetching submissions for user: {} and question: {}", userEmail, questionId);
		List<CodeSubmission> submissions = compilerService.getSubmissionsByUserAndQuestion(userEmail, questionId);

		List<CodeSubmissionResponseDTO> response = mapSubmissionsToDTOs(submissions);
		log.debug("Found {} submissions for question", response.size());

		return ResponseEntity.ok(response);
	}

	/**
	 * Retrieves submissions filtered by pass/fail status.
	 * 
	 * @param userEmail User's email address
	 * @param passed    True for passed submissions, false for failed
	 * @return Filtered list of submissions
	 */
	@GetMapping("/results/{userEmail}/passed/{passed}")
	@PreAuthorize("hasAuthority('COMPILER_RESULTS_READ')")
	public ResponseEntity<List<CodeSubmissionResponseDTO>> getSubmissionsByPassStatus(@PathVariable String userEmail,
			@PathVariable Boolean passed) {

		log.info("Fetching {} submissions for user: {}", passed ? "passed" : "failed", userEmail);
		List<CodeSubmissionResponseDTO> results = compilerService.getSubmissionsByPassStatus(userEmail, passed);

		if (results.isEmpty()) {
			log.debug("No {} submissions found", passed ? "passed" : "failed");
			return ResponseEntity.noContent().build();
		}

		log.debug("Found {} {} submissions", results.size(), passed ? "passed" : "failed");
		return ResponseEntity.ok(results);
	}

	/**
	 * Filters submissions by multiple criteria.
	 * 
	 * @param userEmail  User's email address
	 * @param passed     Pass/fail status (optional)
	 * @param questionId Question ID (optional)
	 * @param language   Programming language (optional)
	 * @return Filtered list of submissions
	 */
	@GetMapping("/results/{userEmail}/filter")
	@PreAuthorize("hasAuthority('COMPILER_RESULTS_READ')")
	public ResponseEntity<List<CodeSubmissionResponseDTO>> filterSubmissions(@PathVariable String userEmail,
			@RequestParam(required = false) Boolean passed, @RequestParam(required = false) String questionId,
			@RequestParam(required = false) String language) {

		log.info("Filtering submissions for user: {} with params - passed: {}, questionId: {}, language: {}", userEmail,
				passed, questionId, language);

		List<CodeSubmission> submissions = codeSubmissionRepository.findByUserEmail(userEmail);

		List<CodeSubmissionResponseDTO> results = submissions.stream()
				.filter(sub -> questionId == null || questionId.equals(sub.getQuestionId()))
				.filter(sub -> language == null || language.equals(sub.getLanguage()))
				.filter(sub -> sub.getTestResults() != null).map(this::mapSubmissionToDTO)
				.filter(dto -> !dto.getTestResults().isEmpty()).collect(Collectors.toList());

		log.debug("Found {} matching submissions", results.size());
		return ResponseEntity.ok(results);
	}

	// Helper methods
	private TestResultEntity createTestResultEntity(TestCaseDTO tc, CodeSubmission submission) {
		TestResultEntity t = new TestResultEntity();
		t.setInput(tc.getInput());
		t.setExpectedOutput(tc.getExpectedOutput());
		t.setSubmission(submission);
		return t;
	}

	private void evaluateTestResults(List<TestResultEntity> testEntities, List<TestResultEntity> results) {
		for (int i = 0; i < results.size(); i++) {
			String expected = testEntities.get(i).getExpectedOutput();
			String actual = results.get(i).getActualOutput();
			testEntities.get(i).setActualOutput(actual);
			testEntities.get(i).setPassed(expected != null && expected.trim().equals(actual.trim()));
		}
	}

	private TestCaseDTO createTestCaseDTO(TestResultEntity result, String input, String expectedOutput) {
		TestCaseDTO dto = new TestCaseDTO();
		dto.setInput(input);
		dto.setExpectedOutput(expectedOutput);
		dto.setActualOutput(result.getActualOutput());
		dto.setPassed(Boolean.TRUE.equals(result.getPassed()));

		if (result.getActualOutput() != null && result.getActualOutput().startsWith("Runtime Error:")) {
			CodeErrorInfo errorInfo = compilerService.parseErrorInfo(result.getActualOutput(),
					result.getSubmission().getLanguage());
			if (errorInfo != null && !errorInfo.getMessage().isBlank()) {
				dto.setErrorInfo(errorInfo);
			}
		}
		return dto;
	}

	private List<TestCaseDTO> mapToTestCaseDTOs(List<TestResultEntity> results, String language) {
		return results.stream().map(r -> createTestCaseDTO(r, r.getInput(), r.getExpectedOutput()))
				.collect(Collectors.toList());
	}

	private List<CodeSubmissionResponseDTO> mapSubmissionsToDTOs(List<CodeSubmission> submissions) {
		return submissions.stream().map(this::mapSubmissionToDTO).collect(Collectors.toList());
	}

	private CodeSubmissionResponseDTO mapSubmissionToDTO(CodeSubmission submission) {
		CodeSubmissionResponseDTO dto = new CodeSubmissionResponseDTO();
		dto.setLanguage(submission.getLanguage());
		dto.setScript(submission.getScript());
		dto.setUserEmail(submission.getUserEmail());
		dto.setCreatedAt(submission.getCreatedAt());
		dto.setQuestionId(submission.getQuestionId());
		dto.setPassed(submission.getPassed());

		dto.setTestResults(
				submission.getTestResults().stream().map(this::mapTestResultToDTO).collect(Collectors.toList()));

		return dto;
	}

	private TestCaseDTO mapTestResultToDTO(TestResultEntity result) {
		TestCaseDTO dto = new TestCaseDTO();
		dto.setInput(result.getInput());
		dto.setExpectedOutput(result.getExpectedOutput());
		dto.setActualOutput(result.getActualOutput());
		dto.setQuestionId(result.getQuestionId());
		dto.setPassed(result.getPassed());
		return dto;
	}

	@GetMapping("/results/by-job-prefix")
	@PreAuthorize("hasAuthority('COMPILER_RESULTS_READ')")
	public ResponseEntity<List<CodeSubmissionResponseDTO>> getResultsByJobPrefix(@RequestParam String jobPrefix) {
		log.info("Fetching all latest submissions for jobPrefix: {}", jobPrefix);
		List<CodeSubmission> submissions = codeSubmissionRepository.findLatestSubmissionsByJobPrefix(jobPrefix);
		List<CodeSubmissionResponseDTO> response = mapSubmissionsToDTOs(submissions);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/results/code")
	@PreAuthorize("hasAuthority('COMPILER_RESULTS_READ')")
	public ResponseEntity<CodeSubmissionResponseDTO> getLatestCodeByEmailJobPrefixAndQuestion(
			@RequestParam String userEmail, @RequestParam String jobPrefix, @RequestParam String questionId) {

		log.info("Fetching latest code for user: {}, jobPrefix: {}, questionId: {}", userEmail, jobPrefix, questionId);

		Optional<CodeSubmission> latestSubmission = compilerService
				.getLatestSubmissionByUserEmailJobPrefixAndQuestionId(userEmail, jobPrefix, questionId);

		if (latestSubmission.isPresent()) {
			CodeSubmissionResponseDTO response = mapSubmissionToDTO(latestSubmission.get());
			return ResponseEntity.ok(response);
		} else {
			log.warn("No code found for user {} and question {}", userEmail, questionId);
			return ResponseEntity.notFound().build();
		}
	}

}