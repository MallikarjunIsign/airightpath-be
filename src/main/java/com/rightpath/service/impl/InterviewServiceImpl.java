package com.rightpath.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.rightpath.dto.InterviewQuestionInfo;
import com.rightpath.dto.StartInterviewResponse;
import com.rightpath.entity.CandidateInterviewSchedule;
import com.rightpath.entity.InterviewQuestion;
import com.rightpath.entity.InterviewSession;
import com.rightpath.entity.JobApplicationForCandidate;
import com.rightpath.enums.ApplicationStatus;
import com.rightpath.enums.AttemptStatus;
import com.rightpath.enums.InterviewResult;
import com.rightpath.repository.CandidateInterviewScheduleRepository;
import com.rightpath.repository.InterviewQuestionRepository;
import com.rightpath.repository.JobApplicationForCandidateRepository;
import com.rightpath.service.InterviewReportService;
import com.rightpath.service.InterviewService;
import com.rightpath.service.JobPromptService;
import com.rightpath.service.OpenAiService;
import com.rightpath.service.StorageService;
import com.rightpath.util.PromptService;
import com.rightpath.util.StatusTransitionValidator;

@Service
public class InterviewServiceImpl implements InterviewService {

	@Value("${aws.s3.prefix.interview:interview}")
	private String interviewPrefix;
	
	@Autowired
	private InterviewQuestionsServiceImpl interviewQuestionsService;
	
	@Autowired
	private InterviewQuestionRepository interviewQuestionRepository;
	
	private final Map<Long, InterviewSession> sessionStore = new HashMap<>();

	private final OpenAiService openAiService;
	private final JobApplicationForCandidateRepository jobAppRepo;
	private final CandidateInterviewScheduleRepository scheduleRepo;
	private final StorageService storageService;
	private final JobPromptService jobPromptService;
	private final com.rightpath.service.EmailAsyncService emailAsyncService;

	public InterviewServiceImpl(OpenAiService openAiService, CandidateInterviewScheduleRepository scheduleRepo,
			JobApplicationForCandidateRepository jobAppRepo, PromptService promptService,
			InterviewReportService interviewReportService, StorageService storageService,
			JobPromptService jobPromptService, com.rightpath.service.EmailAsyncService emailAsyncService) {

		this.openAiService = openAiService;
		this.jobAppRepo = jobAppRepo;
		this.scheduleRepo = scheduleRepo;
		this.storageService = storageService;
		this.jobPromptService = jobPromptService;
		this.emailAsyncService = emailAsyncService;
	}

//	@Override
//	public StartInterviewResponse start(String jobPrefix, String email, String resumeSummary) {
//
//		String systemPrompt = jobPromptService.buildStartPrompt(jobPrefix, resumeSummary);
//
//		String firstQuestion = openAiService.ask(systemPrompt);
//
//		return new StartInterviewResponse(systemPrompt, firstQuestion);
//	}
	@Override
	public StartInterviewResponse start(String jobPrefix, String email, String resumeSummary) {
	    // fetch schedule
	    CandidateInterviewSchedule schedule = scheduleRepo
	            .findTopByJobPrefixAndEmailOrderByAssignedAtDesc(jobPrefix, email)
	            .orElseThrow(() -> new RuntimeException("Interview not assigned"));

	    Long interviewScheduleId = schedule.getId();

	    // load questions (now returns List<InterviewQuestionInfo>)
	    List<InterviewQuestionInfo> questions = interviewQuestionsService.loadAndPrepareQuestions(jobPrefix);

	    // persist question usage counts
	    recordQuestionsForInterview(interviewScheduleId, questions);

	    // create session
	    InterviewSession session = new InterviewSession();
	    session.setJobPrefix(jobPrefix);
	    session.setEmail(email);
	    session.setQuestions(questions);
	    session.setCurrentQuestionIndex(0);
	    session.setResumeSummary(resumeSummary);
	    System.out.println("Saving session with " + questions.size() + " questions");
	    saveSession(interviewScheduleId, session);

	    String firstQuestion = questions.get(0).getText();   // text of the first question
	    return new StartInterviewResponse(null, firstQuestion);
	}

	private void recordQuestionsForInterview(Long scheduleId, List<InterviewQuestionInfo> questions) {
	    // Group by uniqueId – use getter method reference
	    Map<String, Long> countByUniqueId = questions.stream()
	            .collect(Collectors.groupingBy(InterviewQuestionInfo::getUniqueId, Collectors.counting()));

	    for (Map.Entry<String, Long> entry : countByUniqueId.entrySet()) {
	        String uniqueId = entry.getKey();
	        long count = entry.getValue();

	        Optional<InterviewQuestion> existing = interviewQuestionRepository
	                .findByInterviewScheduleIdAndUniqueId(scheduleId, uniqueId);

	        if (existing.isPresent()) {
	            InterviewQuestion iq = existing.get();
	            iq.setCount(iq.getCount() + (int) count);
	            interviewQuestionRepository.save(iq);
	        } else {
	            // retrieve the first occurrence of this uniqueId in the list
	            InterviewQuestionInfo first = questions.stream()
	                    .filter(q -> q.getUniqueId().equals(uniqueId))
	                    .findFirst()
	                    .orElseThrow(() -> new RuntimeException("Question not found for uniqueId: " + uniqueId));

	            InterviewQuestion newQ = new InterviewQuestion();
	            newQ.setInterviewScheduleId(scheduleId);
	            newQ.setUniqueId(uniqueId);
	            newQ.setLevel(first.getLevel());
	            newQ.setQuestionText(first.getText());   // don't forget to store the question text
	            newQ.setCount((int) count);
	            interviewQuestionRepository.save(newQ);
	        }
	    }
	}
//	@Override
//	public String answer(Long interviewScheduleId, String conversationHistory, boolean finalAnswer, String jobPrefix) {
//
//		if (conversationHistory == null || conversationHistory.isBlank()) {
//			throw new IllegalArgumentException("Conversation history cannot be null or empty");
//		}
//
//		if (finalAnswer) {
//			if (jobPrefix == null || jobPrefix.isBlank()) {
//				throw new IllegalArgumentException("jobPrefix cannot be null or empty");
//			}
//			String summary = generateFinalSummary(conversationHistory, jobPrefix);
//
//			markCompleted(interviewScheduleId, AttemptStatus.COMPLETED, InterviewResult.PENDING, summary);
//
//			return summary;
//		}
//
//		return openAiService.ask(conversationHistory);
//	}
	
	@Override
	public String answer(Long interviewScheduleId,
	                     String answerText,
	                     boolean finalAnswer,
	                     String jobPrefix,
	                     String codeContent,
	                     String codeLanguage) {

	    InterviewSession session = getSession(interviewScheduleId);
	    List<InterviewQuestionInfo> questions = session.getQuestions();
	    System.out.println("Total questions: " + questions.size());
	    System.out.println("Session has " + session.getQuestions().size() + " questions");
	    int currentIndex = session.getCurrentQuestionIndex();
	    System.out.println("Current index: " + currentIndex);
	    boolean retryAlreadyUsed = session.getRetryUsed().getOrDefault(currentIndex, false);
	    System.out.println("Retry already used for index " + currentIndex + ": " + retryAlreadyUsed);

	    // Build the full answer (with code if any)
	    String answerWithCode = answerText;
	    if (codeContent != null && !codeContent.isBlank()) {
	        answerWithCode += "\n\n[CODE (" + (codeLanguage != null ? codeLanguage : "text") + ")]\n" + codeContent;
	    }

	    // Append to history ONLY if we are not in a retry situation
	    // We'll store after the retry decision

	    if (finalAnswer) {
	        session.appendQAPair(questions.get(currentIndex).getText(), answerWithCode);
	        String fullHistory = session.getQaHistoryAsString();
	        return generateFinalSummary(fullHistory, jobPrefix);
	    }

	    // Build evaluation prompt (same as before, includes language warning instruction)
	    String systemPrompt = jobPromptService.buildStartPrompt(jobPrefix, session.getResumeSummary());
	    String evaluationPrompt = systemPrompt + "\n\n" +
	            "If candidate answer other than English, warn him and ask him to answer in English only.\n" +
	            "Now evaluate the following answer. Your response must include:\n" +
	            "1. Classification (choose one):\n" +
	            "   - Base answered: Totally correct\n" +
	            "   - Base answered: Partially correct\n" +
	            "   - Base not answered\n" +
	            "   - Level 1 answered\n" +
	            "   - Level 2 answered\n" +
	            "2. Short, constructive feedback:\n" +
	            "   - If correct: acknowledge briefly.\n" +
	            "   - If partially correct: guide improvement.\n" +
	            "   - If incorrect: explain the key concept briefly.\n\n" +
	            "Focus on technical correctness and clarity of explanation.\n" +
	            "For coding answers, check correct logic, code structure, and explanation clarity.\n" +
	            "Be professional and concise. Do NOT ask questions or generate new questions.\n\n" +
	            "Question: " + questions.get(currentIndex).getText() + "\n" +
	            "Candidate's Answer: " + answerWithCode;

	    String aiFeedback = openAiService.ask(evaluationPrompt);
	    
	    

	    // --- Check if AI suggests the candidate should answer in English ---
	    boolean isLanguageWarning = aiFeedback.toLowerCase().contains("answer in english") ||
                aiFeedback.toLowerCase().contains("non-english") ||
                aiFeedback.toLowerCase().contains("english only");

			if (!retryAlreadyUsed && isLanguageWarning) {
			// First non-English attempt: give a retry
			session.getRetryUsed().put(currentIndex, true);
			// Do NOT store the answer, do NOT advance index
			return "RETRY: " + aiFeedback + "\n\nPlease answer the same question again.\n\nQuestion: " + questions.get(currentIndex).getText();
			}
			
			//Normal flow (English answer, or second attempt regardless of language)
			session.appendQAPair(questions.get(currentIndex).getText(), answerWithCode);
			int nextIndex = currentIndex + 1;
			session.setCurrentQuestionIndex(nextIndex);

	    if (nextIndex >= questions.size()) {
	        String fullHistory = session.getQaHistoryAsString();
	        return generateFinalSummary(fullHistory, jobPrefix);
	    }

	    InterviewQuestionInfo nextQuestion = questions.get(nextIndex);
	    System.out.println("AI Feedback: " + aiFeedback);
	    String returnValue = "FEEDBACK: " + aiFeedback + "\n\nNEXT QUESTION: " + nextQuestion.getText();
	    System.out.println("Returning: " + returnValue);
	    return "FEEDBACK: " + aiFeedback + "\n\nNEXT QUESTION: " + nextQuestion.getText();
	}
	
//	private String generateFinalSummary(String conversationHistory, String jobPrefix) {
//
//		String summaryPrompt = jobPromptService.buildSummaryPrompt(jobPrefix, conversationHistory);
//
//		summaryPrompt = summaryPrompt
//				+ "\n\nProvide all the questions and answers in order, followed by a final summary. "
//				+ "Do not include the system prompt in your response.";
//
//		return openAiService.ask(summaryPrompt);
//	}

//	private String generateFinalSummary(String conversationHistory, String jobPrefix) {
//	    return "HISTORY:\n" + conversationHistory;  
//	}
	
	private String generateFinalSummary(String conversationHistory, String jobPrefix) {
	    String summaryPrompt = 
	        "Please provide a final summary of this interview based on the following Q&A.\n" +
	        "List all questions and answers in order, then provide an overall assessment.\n\n" +
	        "Q&A History:\n" + conversationHistory;
	    return openAiService.ask(summaryPrompt);
	}
	
	@Override
	public CandidateInterviewSchedule assignInterview(String jobPrefix, String email, LocalDateTime assignedAt,
			LocalDateTime deadlineTime) {

		JobApplicationForCandidate application = jobAppRepo.findByJobPost_JobPrefixAndUser_Email(jobPrefix, email)
				.orElseThrow(() -> new RuntimeException("Application not found"));

		application.setInterview("Scheduled");
		jobAppRepo.save(application);

		CandidateInterviewSchedule schedule = CandidateInterviewSchedule.builder().jobPrefix(jobPrefix).email(email)
				.attemptStatus(AttemptStatus.NOT_ATTEMPTED).interviewResult(InterviewResult.PENDING)
				.assignedAt(assignedAt).deadlineTime(deadlineTime).build();

		return scheduleRepo.save(schedule);
	}

	@Override
	public List<CandidateInterviewSchedule> getActiveInterviewsByEmail(String email) {
		return scheduleRepo.findActiveInterviewsByEmail(email);
	}

	@Override
	public List<CandidateInterviewSchedule> assignInterviewBulk(String jobPrefix, java.util.List<String> emails,
			LocalDateTime assignedAt, LocalDateTime deadlineTime, boolean sendEmail) {

		if (emails == null || emails.isEmpty()) {
			throw new IllegalArgumentException("emails must be provided");
		}

		java.util.List<CandidateInterviewSchedule> schedules = new java.util.ArrayList<>();

		for (String email : emails) {
			JobApplicationForCandidate application = jobAppRepo
					.findByJobPost_JobPrefixAndUser_Email(jobPrefix, email)
					.orElseThrow(() -> new RuntimeException("Application not found for " + email));

			application.setInterview("Scheduled");
			jobAppRepo.save(application);

			CandidateInterviewSchedule schedule = CandidateInterviewSchedule.builder().jobPrefix(jobPrefix)
					.email(email).attemptStatus(AttemptStatus.NOT_ATTEMPTED).interviewResult(InterviewResult.PENDING)
					.assignedAt(assignedAt).deadlineTime(deadlineTime).build();

			schedules.add(schedule);
		}

		schedules = scheduleRepo.saveAll(schedules);

		if (sendEmail) {
			String subject = "Interview Scheduled - " + jobPrefix;
			for (CandidateInterviewSchedule s : schedules) {
				String body = "Your interview for job " + jobPrefix + " is scheduled. Deadline: " + deadlineTime;
				emailAsyncService.sendInterviewEmail(s.getEmail(), subject, body);
			}
		}

		return schedules;
	}

	@Transactional
	@Override
	public void markCompleted(Long interviewScheduleId, AttemptStatus attemptStatus, InterviewResult interviewResult,
			String summaryRef) {
		CandidateInterviewSchedule schedule = scheduleRepo.findById(interviewScheduleId)
				.orElseThrow(() -> new RuntimeException("Interview not found"));

		schedule.setAttemptStatus(attemptStatus);
		schedule.setInterviewResult(interviewResult);
		schedule.setSummaryReferences(summaryRef);

		scheduleRepo.save(schedule);

		// If interview completed, update the job application status
		if (attemptStatus == AttemptStatus.COMPLETED) {
			jobAppRepo.findByJobPost_JobPrefixAndUser_Email(schedule.getJobPrefix(), schedule.getEmail())
					.ifPresent(application -> {
						if (application.getStatus() == ApplicationStatus.INTERVIEW_SCHEDULED) {
							StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.INTERVIEW_COMPLETED);
							application.setStatus(ApplicationStatus.INTERVIEW_COMPLETED);
							application.setInterview("Completed");
							jobAppRepo.save(application);
						}
					});
		}
	}

	@Override
	@Transactional
	public String storeRecording(Long interviewScheduleId, MultipartFile videoFile) {

		if (videoFile == null || videoFile.isEmpty()) {
			throw new IllegalArgumentException("Video file is required");
		}

		CandidateInterviewSchedule schedule = scheduleRepo.findById(interviewScheduleId)
				.orElseThrow(() -> new RuntimeException("Interview not found"));

		String blobName = buildBlobName(interviewScheduleId, videoFile.getOriginalFilename());

		String videoUrl = storageService.uploadFile(interviewPrefix, blobName, videoFile);

		schedule.setRecordReferences(videoUrl);
		scheduleRepo.save(schedule);

		return videoUrl;
	}

	@Override
	@Transactional
	public String storeScreenRecording(Long interviewScheduleId, MultipartFile screenFile) {

		if (screenFile == null || screenFile.isEmpty()) {
			throw new IllegalArgumentException("Screen recording file is required");
		}

		CandidateInterviewSchedule schedule = scheduleRepo.findById(interviewScheduleId)
				.orElseThrow(() -> new RuntimeException("Interview not found"));

		String blobName = buildBlobName(interviewScheduleId, screenFile.getOriginalFilename());

		String screenUrl = storageService.uploadFile(interviewPrefix, blobName, screenFile);

		schedule.setScreenRecordReferences(screenUrl);
		scheduleRepo.save(schedule);

		return screenUrl;
	}

	@Override
	public List<CandidateInterviewSchedule> getResults(String jobPrefix) {
		if (jobPrefix != null && !jobPrefix.isBlank()) {
			return scheduleRepo.findAllByJobPrefix(jobPrefix);
		}
		return scheduleRepo.findAll();
	}

	@Override
	public CandidateInterviewSchedule getResultDetail(Long id) {
		return scheduleRepo.findById(id)
				.orElseThrow(() -> new RuntimeException("Interview schedule not found: " + id));
	}

	private String buildBlobName(Long interviewId, String originalName) {
		return "interview-" + interviewId + "/" + System.currentTimeMillis() + "-" + originalName;
	}

	private void saveSession(Long interviewScheduleId, InterviewSession session) {
	    sessionStore.put(interviewScheduleId, session);
	}
	
	private InterviewSession getSession(Long interviewScheduleId) {

	    InterviewSession session = sessionStore.get(interviewScheduleId);

	    if (session == null) {
	        throw new RuntimeException("Session not found for interviewScheduleId: " + interviewScheduleId);
	    }

	    return session;
	}
	
	@Override
	public String prepareQuestionsAndCreateSession(String jobPrefix, String email, Long scheduleId) {
	    // Load questions from S3
	    List<InterviewQuestionInfo> questions = interviewQuestionsService.loadAndPrepareQuestions(jobPrefix);
	    
	    // Record question usage (optional)
	    recordQuestionsForInterview(scheduleId, questions);
	    
	    // Create and store session
	    InterviewSession session = new InterviewSession();
	    session.setJobPrefix(jobPrefix);
	    session.setEmail(email);
	    session.setQuestions(questions);
	    session.setCurrentQuestionIndex(0);
	    session.setResumeSummary(null);   // or pass if available
	    saveSession(scheduleId, session);
	    
	    // Return the first question text
	    return questions.get(0).getText();
	}
	
}
