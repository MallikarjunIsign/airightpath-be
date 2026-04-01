package com.rightpath.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightpath.dto.AssessmentUploadDto;
import com.rightpath.dto.AssignAssessmentBlobDto;
import com.rightpath.dto.AssignAssessmentDto;
import com.rightpath.entity.Assessment;
import com.rightpath.entity.JobApplicationForCandidate;
import com.rightpath.entity.Result;
import com.rightpath.enums.ApplicationStatus;
import com.rightpath.enums.AssessmentType;
import com.rightpath.enums.ResultStatus;
import com.rightpath.repository.AssessmentRepository;
import com.rightpath.repository.JobApplicationForCandidateRepository;
import com.rightpath.repository.ResultRepository;
import com.rightpath.service.AssessmentService;
import com.rightpath.service.EmailService;
import com.rightpath.service.StorageService;
import com.rightpath.util.StatusTransitionValidator;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Transactional
@Service
public class AssessmentServiceImpl implements AssessmentService {

	@Value("${aws.s3.prefix.exam:exam}")
	private String examPrefix;

	private static final Logger logger = LoggerFactory.getLogger(AssessmentServiceImpl.class);


	@Autowired
	private final AssessmentRepository assessmentRepository;
	private final StorageService storageService;

	private JobApplicationForCandidateRepository jobApplicationRepository;

	@Autowired
	private final ResultRepository resultRepository;
	@Autowired
	private final EmailService emailService;

	public AssessmentServiceImpl(AssessmentRepository assessmentRepository, ResultRepository resultRepository,
			EmailService emailService, JobApplicationForCandidateRepository jobApplicationRepository,
			StorageService storageService) {
		super();
		this.assessmentRepository = assessmentRepository;
		this.resultRepository = resultRepository;
		this.emailService = emailService;
		this.jobApplicationRepository = jobApplicationRepository;
		this.storageService = storageService;
	}

	/**
	 * Uploads a new assessment to the system.
	 *
	 * @param dto The DTO containing assessment details and files.
	 * @return A success message.
	 */
	@Override
	public String uploadAssessment(AssessmentUploadDto dto) {
		logger.info("Uploading assessment: {}", dto);
		try {
			MultipartFile questionPaperFile = dto.getQuestionPaper();

			Assessment assessment = new Assessment();
			assessment.setAssessmentType(AssessmentType.valueOf(dto.getAssessmentType()));
			assessment.setCandidateEmail(dto.getCandidateEmail());
			assessment.setUploadedBy(dto.getUploadedBy());
			assessment.setDeadline(dto.getDeadline());
			assessment.setAdminAcceptance(dto.isAdminAcceptance());
			assessment.setAdminComments(dto.getAdminComments());
			assessment.setQuestionPaper(new String(questionPaperFile.getBytes(), StandardCharsets.UTF_8));

			if (dto.getAnswerKey() != null && !dto.getAnswerKey().isEmpty()) {
				assessment.setAnswerKey(dto.getAnswerKey().getBytes());
			}

			assessmentRepository.save(assessment);
			logger.info("Assessment uploaded successfully for candidate: {}", dto.getCandidateEmail());
			return "Assessment uploaded successfully.";
		} catch (IOException e) {
			logger.error("Error uploading assessment for candidate: {}", dto.getCandidateEmail(), e);
			throw new RuntimeException("Error uploading files", e);
		}
	}

	/**
	 * Retrieves active assessments assigned to a specific candidate.
	 *
	 * @param candidateEmail The email of the candidate.
	 * @return A list of active assessments.
	 */
	@Override
	public List<Assessment> getCandidateAssessments(String candidateEmail) {
	    List<Assessment> activeAssessments =
	            assessmentRepository.findActiveAssessments(candidateEmail);

	    logger.info("Fetched {} active assessments for candidate: {}",
	            activeAssessments.size(), candidateEmail);

	    return activeAssessments; // return empty list if none
	}

	/**
	 * Fetches a specific assessment's details using its ID.
	 *
	 * @param assessmentId The ID of the assessment.
	 * @return The assessment entity.
	 */
	@Override
	public Assessment fetchAssessmentDetails(Long assessmentId) {
		Assessment assessment = assessmentRepository.findById(assessmentId)
				.orElseThrow(() -> new RuntimeException("Assessment not found"));
		resolveQuestionPaperFromStorage(assessment);
		return assessment;
	}

	/**
	 * Records the result of an assessment for a candidate.
	 *
	 * @param candidateEmail The candidate's email.
	 * @param assessmentType The type of the assessment.
	 * @param score          The score achieved by the candidate.
	 * @param jsonData       The detailed result in JSON format.
	 * @return A success message.
	 */
	@Override
	public String resultAssessment(String candidateEmail, String assessmentType, Double score, String jsonData,
			String jobPrefix) {
		logger.info("Recording result for candidate: {}, type: {}, jobPrefix: {}", candidateEmail, assessmentType,
				jobPrefix);

		List<Assessment> assessments = assessmentRepository.findByCandidateEmail(candidateEmail);
		assessments.stream().filter(a -> a.getAssessmentType().name().equalsIgnoreCase(assessmentType)).findFirst()
				.ifPresent(assessment -> {
					assessment.setExamAttended(true);
					assessmentRepository.save(assessment);
					logger.info("Marked assessment as attended for candidate: {}", candidateEmail);

					// Check if all assessments for this candidate+job are completed
					if (jobPrefix != null) {
						long pending = assessmentRepository.countByCandidateEmailAndJobPrefixAndExamAttendedFalse(
								candidateEmail, jobPrefix);
						if (pending == 0) {
							List<JobApplicationForCandidate> apps = jobApplicationRepository
									.findByJobPrefixAndEmail(jobPrefix, candidateEmail);
							for (JobApplicationForCandidate app : apps) {
								if (app.getStatus() == ApplicationStatus.EXAM_SENT) {
									StatusTransitionValidator.validate(app.getStatus(), ApplicationStatus.EXAM_COMPLETED);
									app.setStatus(ApplicationStatus.EXAM_COMPLETED);
									jobApplicationRepository.save(app);
								}
							}
						}
					}
				});

		Result result = new Result();
		result.setCandidateEmail(candidateEmail);
		result.setAssessmentType(AssessmentType.valueOf(assessmentType));
		result.setScore(score);
		result.setStatus(score >= 50 ? ResultStatus.PASSED : ResultStatus.FAILED);
		result.setSubmittedAt(LocalDateTime.now());
		result.setResultsJson(jsonData);
		result.setJobPrefix(jobPrefix);

		resultRepository.save(result);

		logger.info("Assessment result saved successfully for candidate: {}", candidateEmail);
		return "Assessment submitted successfully.";
	}

	/**
	 * Submits an assessment by marking it as attended and sends a confirmation
	 * email.
	 *
	 * @param id The ID of the assessment.
	 * @return A success message.
	 */
	@Override
	public String submitAssessment(Long id) {
		logger.info("Submitting assessment with ID: {}", id);

		Assessment assessment = assessmentRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Assessment not found for ID: " + id));

		if (assessment.isExamAttended()) {
			logger.warn("Assessment already submitted for ID: {}", id);
			throw new IllegalStateException("Assessment has already been submitted.");
		}

		assessment.setExamAttended(true);
		assessmentRepository.save(assessment);

		String candidateEmail = assessment.getCandidateEmail();
		String jobPrefix = assessment.getJobPrefix();

		if (candidateEmail != null && !candidateEmail.isEmpty() && jobPrefix != null) {
			// Update job application status
			List<JobApplicationForCandidate> applications = jobApplicationRepository.findByJobPrefixAndEmail(jobPrefix,
					candidateEmail);
			for (JobApplicationForCandidate application : applications) {
				application.setExamCompletedStatus("Aptitude Completed");

				// Check if all assessments for this candidate+job are completed
				long pending = assessmentRepository.countByCandidateEmailAndJobPrefixAndExamAttendedFalse(
						candidateEmail, jobPrefix);
				if (pending == 0 && application.getStatus() == ApplicationStatus.EXAM_SENT) {
					StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.EXAM_COMPLETED);
					application.setStatus(ApplicationStatus.EXAM_COMPLETED);
				}

				jobApplicationRepository.save(application);
			}

			emailService.sendSuccessExamAttend(candidateEmail, jobPrefix);
			logger.info("Email notification sent and job application status updated for: {}", candidateEmail);
		} else {
			logger.warn("Candidate email or jobPrefix not available for assessment ID: {}", id);
		}

		return "Assessment submitted successfully.";
	}

	/**
	 * Retrieves assessments for a given candidate or all assessments if requested.
	 *
	 * @param candidateEmail The candidate's email.
	 * @param fetchAll       Flag to indicate if all records should be fetched.
	 * @return A list of assessments.
	 */
	@Override
	public List<Assessment> getAssessments(String candidateEmail, boolean fetchAll) {
		if (fetchAll) {
			logger.info("Fetching all assessments.");
			return assessmentRepository.findAll();
		} else {
			logger.info("Fetching assessments for candidate: {}", candidateEmail);
			return assessmentRepository.findByCandidateEmail(candidateEmail);
		}
	}

	/**
	 * Expires all assessments that have passed their deadline.
	 */
	@Override
	public void expireAssessments() {
		logger.info("Expiring overdue assessments...");
		List<Assessment> expiredAssessments = assessmentRepository.findExpiredAssessments();
		for (Assessment assessment : expiredAssessments) {
			assessment.setExpired(true);
			assessmentRepository.save(assessment);
			logger.debug("Expired assessment ID: {}", assessment.getId());
		}
	}

	/**
	 * Assigns an assessment to multiple candidates and sends exam links via email.
	 *
	 * @param dto The DTO containing assessment assignment details.
	 * @return A success message.
	 */
	@Override
	public String assignAssessment(AssignAssessmentDto dto, String jobPrefix) {
		for (String email : dto.getCandidateEmails()) {
			// Process Aptitude Assessment
			if (dto.getAptitudeQuestionPaper() != null && !dto.getAptitudeQuestionPaper().isEmpty()) {
				Assessment aptitudeAssessment = new Assessment();
				aptitudeAssessment.setAssessmentType(AssessmentType.APTITUDE);
				aptitudeAssessment.setCandidateEmail(email);
				aptitudeAssessment.setUploadedBy(dto.getUploadedBy());
				String aptFileName = jobPrefix + "_aptitude_" + email.replace("@", "_") + "_" + System.currentTimeMillis() + ".json";
				storageService.uploadFile(examPrefix, aptFileName, dto.getAptitudeQuestionPaper());
				aptitudeAssessment.setContainerName(examPrefix);
				aptitudeAssessment.setFileName(aptFileName);
				aptitudeAssessment.setAssignedAt(LocalDateTime.now());
				aptitudeAssessment.setStartTime(dto.getStartTime());
				aptitudeAssessment.setDeadline(dto.getDeadline());
				aptitudeAssessment.setJobPrefix(jobPrefix);
				aptitudeAssessment.setAdminAcceptance(dto.isAdminAcceptance());
				aptitudeAssessment.setAdminComments(dto.getAdminComments());

				if (dto.getAptitudeAnswerKey() != null) {
					try {
						aptitudeAssessment.setAnswerKey(dto.getAptitudeAnswerKey().getBytes());
					} catch (IOException e) {
						throw new RuntimeException("Error reading aptitude answer key", e);
					}
				}
				assessmentRepository.save(aptitudeAssessment);
			}

			// Process Coding Assessment (without answer key)
			if (dto.getCodingQuestionPaper() != null && !dto.getCodingQuestionPaper().isEmpty()) {
				Assessment codingAssessment = new Assessment();
				codingAssessment.setAssessmentType(AssessmentType.CODING);
				codingAssessment.setCandidateEmail(email);
				codingAssessment.setUploadedBy(dto.getUploadedBy());
				String codingFileName = jobPrefix + "_coding_" + email.replace("@", "_") + "_" + System.currentTimeMillis() + ".json";
				storageService.uploadFile(examPrefix, codingFileName, dto.getCodingQuestionPaper());
				codingAssessment.setContainerName(examPrefix);
				codingAssessment.setFileName(codingFileName);
				codingAssessment.setAssignedAt(LocalDateTime.now());
				codingAssessment.setStartTime(dto.getStartTime());
				codingAssessment.setDeadline(dto.getDeadline());
				codingAssessment.setJobPrefix(jobPrefix);
				codingAssessment.setAdminAcceptance(dto.isAdminAcceptance());
				codingAssessment.setAdminComments(dto.getAdminComments());
				assessmentRepository.save(codingAssessment);
			}

			// Send email and update status
			emailService.sendExamLink(email, dto.getStartTime(), dto.getDeadline(), jobPrefix);
			updateApplicationStatus(jobPrefix, email);
		}
		return "Assessments assigned successfully.";
	}

	private void updateApplicationStatus(String jobPrefix, String email) {
		List<JobApplicationForCandidate> applications = jobApplicationRepository.findByJobPrefixAndEmail(jobPrefix,
				email);
		for (JobApplicationForCandidate application : applications) {
			StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.EXAM_SENT);
			application.setStatus(ApplicationStatus.EXAM_SENT);
			application.setExamLinkStatus("Exam Link Sent");
			jobApplicationRepository.save(application);
		}
	}

	/**
	 * Scheduled task that updates expired assessments every minute.
	 */
	@Scheduled(cron = "0 * * * * *")
	@Transactional
	public void updateExpiredAssessments() {
		int updatedCount = assessmentRepository.updateExpiredAssessments();
		logger.info("Updated expired assessments count: {}", updatedCount);
	}

	/**
	 * Retrieves results for a specific candidate using their email.
	 *
	 * @param email The candidate's email address.
	 * @return A list of results.
	 */
	@Override
	public List<Result> getResultsByEmail(String email) {
		if (email == null || email.isEmpty()) {
			throw new IllegalArgumentException("Email cannot be null or empty");
		}
		logger.info("Fetching results for candidate: {}", email);
		return resultRepository.findByCandidateEmail(email);
	}

	/**
	 * Fetches a specific result record by its ID.
	 *
	 * @param id The result ID.
	 * @return An Optional containing the result if found.
	 */
	@Override
	public Optional<Result> getResultById(Long id) {
		logger.info("Fetching result with ID: {}", id);
		return resultRepository.findById(id);

	}

	public void markExamAsAttended(String email, Long assessmentId) {
		Optional<Assessment> assessmentOpt = assessmentRepository.findById(assessmentId);
		if (assessmentOpt.isEmpty()) {
			throw new RuntimeException("Assessment not found with ID: " + assessmentId);
		}

		Assessment assessment = assessmentOpt.get();
		assessment.setExamAttended(true);
		assessmentRepository.save(assessment);

		List<JobApplicationForCandidate> applications = jobApplicationRepository
				.findByJobPrefixAndEmail(assessment.getJobPrefix(), email);

		JobApplicationForCandidate application = applications.get(0);
		String mobileNumber = application.getMobileNumber();

		if (applications.isEmpty()) {
			throw new RuntimeException("No job application found for the given jobPrefix and email.");
		}

		for (JobApplicationForCandidate app : applications) {
			app.setExamCompletedStatus("Exam Completed");
			jobApplicationRepository.save(app);
			emailService.sendSuccessCodingExamAttend(email, assessment.getJobPrefix());

		}
	}

	@Override
	public String getLatestQuestionPaper(String email, String jobPrefix, String assessmentType) {

		Assessment assessment = assessmentRepository
				.findTopByCandidateEmailAndJobPrefixAndAssessmentTypeOrderByAssignedAtDesc(email, jobPrefix,
						AssessmentType.valueOf(assessmentType))
				.orElseThrow(() -> new IllegalStateException("No assessment found for the given details."));

		resolveQuestionPaperFromStorage(assessment);
		return assessment.getQuestionPaper();
	}

	@Override
	public String assignAssessmentToStorage(AssignAssessmentBlobDto dto) {
		try {
			for (String email : dto.getCandidateEmails()) {
				String fileUrl = storageService.uploadFile(examPrefix, dto.getFileName(), dto.getFile());
				Assessment assessment = new Assessment();
				assessment.setAssessmentType(AssessmentType.valueOf(dto.getAssessmentType()));
				assessment.setCandidateEmail(email);
				assessment.setUploadedBy(dto.getUploadedBy());
				assessment.setAssignedAt(LocalDateTime.now());
				assessment.setStartTime(dto.getStartTime());
				assessment.setDeadline(dto.getDeadline());
				assessment.setJobPrefix(dto.getJobPrefix());
				assessment.setContainerName(examPrefix);
				assessment.setFileName(dto.getFileName());
				assessmentRepository.save(assessment);
				emailService.sendExamLink(email, dto.getStartTime(), dto.getDeadline(), dto.getJobPrefix());
				updateApplicationStatus(dto.getJobPrefix(), email);
			}
			return "Assessments assigned successfully to storage.";
		} catch (Exception e) {
			throw new RuntimeException("Error uploading file to storage", e);
		}
	}

	@Override
	public String downloadFileContentFromStorage(String containerName, String fileName) {
		byte[] fileBytes = storageService.downloadFile(containerName, fileName);
		return new String(fileBytes, StandardCharsets.UTF_8);
	}

	private void resolveQuestionPaperFromStorage(Assessment assessment) {
		if (assessment.getQuestionPaper() == null
				&& assessment.getContainerName() != null
				&& assessment.getFileName() != null) {
			String content = downloadFileContentFromStorage(
					assessment.getContainerName(), assessment.getFileName());
			assessment.setQuestionPaper(content);
		}
	}

	public List<Map<String, Object>> getLatestAssessmentContent(String jobPrefix, String candidateEmail,
			String assessmentType) {

		Assessment assessment = assessmentRepository
				.findTopByJobPrefixAndCandidateEmailAndAssessmentTypeOrderByAssignedAtDesc(jobPrefix, candidateEmail,
						AssessmentType.valueOf(assessmentType))
				.orElseThrow(() -> new RuntimeException("No assessment found for given inputs: " + candidateEmail + ", "
						+ jobPrefix + ", " + assessmentType));

		String fileContent = downloadFileContentFromStorage(assessment.getContainerName(), assessment.getFileName());

		try {
			ObjectMapper objectMapper = new ObjectMapper();
			return objectMapper.readValue(fileContent, new TypeReference<List<Map<String, Object>>>() {
			});
		} catch (IOException e) {
			throw new RuntimeException("Failed to parse assessment JSON from storage", e);
		}
	}

	@Override
	public List<Result> getResultsByEmailAndJobPrefix(String email, String jobPrefix) {
		if (email == null || jobPrefix == null) {
			throw new IllegalArgumentException("Email and jobPrefix must not be null");
		}
		return resultRepository.findByCandidateEmailAndJobPrefix(email, jobPrefix);
	}

}
