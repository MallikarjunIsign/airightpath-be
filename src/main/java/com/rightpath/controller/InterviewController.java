package com.rightpath.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rightpath.dto.AssignInterviewDTO;
import com.rightpath.dto.AssignInterviewBulkDTO;
import com.rightpath.dto.CandidateInterviewScheduleDTO;
import com.rightpath.dto.InterviewStatsDTO;
import com.rightpath.dto.StartInterviewRequest;
import com.rightpath.dto.StartInterviewResponse;
import com.rightpath.dto.voice.ResumeResponse;
import com.rightpath.dto.voice.VoiceEvaluationResult;
import com.rightpath.dto.voice.VoiceSessionStatus;
import com.rightpath.dto.voice.VoiceStartResponse;
import com.rightpath.entity.CandidateInterviewSchedule;
import com.rightpath.entity.ProctoringEvent;
import com.rightpath.entity.RoomVerificationSession;
import com.rightpath.entity.VoiceConversationEntry;
import com.rightpath.enums.AttemptStatus;
import com.rightpath.enums.InterviewResult;
import com.rightpath.repository.ProctoringEventRepository;
import com.rightpath.repository.VoiceConversationEntryRepository;
import com.rightpath.service.InterviewQuestionsService;
import com.rightpath.service.InterviewService;
import com.rightpath.service.RoomVerificationService;
import com.rightpath.service.SpeechToTextService;
import com.rightpath.service.VoiceInterviewService;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

	private static final Logger log = LoggerFactory.getLogger(InterviewController.class);
	
	@Autowired
	private InterviewQuestionsService interviewQuestionsService;

	@Autowired
	private InterviewService interviewService;
	@Autowired
	private SpeechToTextService speechToTextService;
	@Autowired
	private com.rightpath.service.EmailService emailService;
	@Autowired
	private VoiceInterviewService voiceInterviewService;
	@Autowired
	private ProctoringEventRepository proctoringEventRepository;
	@Autowired
	private VoiceConversationEntryRepository voiceConversationEntryRepository;
	
	@Autowired
	private RoomVerificationService roomVerificationService;

	@PostMapping("/assign-interview")
	@PreAuthorize("hasAuthority('INTERVIEW_ASSIGN')")
	public ResponseEntity<?> assignInterview(@RequestBody AssignInterviewDTO dto) {

		CandidateInterviewSchedule schedule = interviewService.assignInterview(dto.getJobPrefix(), dto.getEmail(),
				dto.getAssignedAt(), dto.getDeadlineTime());

		return ResponseEntity.ok(new CandidateInterviewScheduleDTO(schedule));
	}

	@PostMapping("/assign-interview-bulk")
	@PreAuthorize("hasAuthority('INTERVIEW_ASSIGN')")
	public ResponseEntity<?> assignInterviewBulk(@RequestBody AssignInterviewBulkDTO dto) {

		java.time.LocalDateTime assignedAt = dto.getAssignedAt() != null ? dto.getAssignedAt()
				: java.time.LocalDateTime.now();

		java.util.List<com.rightpath.entity.CandidateInterviewSchedule> schedules = interviewService
				.assignInterviewBulk(dto.getJobPrefix(), dto.getEmails(), assignedAt, dto.getDeadlineTime(),
						dto.isSendEmail());

		java.util.List<CandidateInterviewScheduleDTO> results = schedules.stream()
				.map(CandidateInterviewScheduleDTO::new).toList();

		return ResponseEntity.ok(results);
	}

	@GetMapping("/results")
	@PreAuthorize("hasAuthority('INTERVIEW_ASSIGN')")
	public ResponseEntity<?> getResults(@RequestParam(required = false) String jobPrefix) {
		List<CandidateInterviewSchedule> results = interviewService.getResults(jobPrefix);
		List<CandidateInterviewScheduleDTO> dtoList = results.stream()
				.map(CandidateInterviewScheduleDTO::new).toList();
		return ResponseEntity.ok(dtoList);
	}

	@GetMapping("/results/{id}")
	@PreAuthorize("hasAuthority('INTERVIEW_ASSIGN')")
	public ResponseEntity<?> getResultDetail(@PathVariable Long id) {
		CandidateInterviewSchedule schedule = interviewService.getResultDetail(id);
		return ResponseEntity.ok(new CandidateInterviewScheduleDTO(schedule));
	}

	@GetMapping("/active")
	@PreAuthorize("hasAuthority('INTERVIEW_START')")
	public ResponseEntity<?> getActiveInterviews(@RequestParam String email) {

		List<CandidateInterviewSchedule> list = interviewService.getActiveInterviewsByEmail(email);

		List<CandidateInterviewScheduleDTO> dtoList = list.stream().map(CandidateInterviewScheduleDTO::new).toList();

		return ResponseEntity.ok(dtoList);
	}

	@PostMapping("/start")
	@PreAuthorize("hasAuthority('INTERVIEW_START')")
	public StartInterviewResponse start(@RequestBody StartInterviewRequest req) {
		return interviewService.start(req.getJobPrefix(), req.getEmail(), req.getResumeSummary());
	}

//	@PostMapping("/answer")
//	@PreAuthorize("hasAuthority('INTERVIEW_ANSWER')")
//	public String answerQuestion(@RequestParam Long interviewScheduleId,
//			@RequestParam(required = false) String conversationHistory, @RequestParam boolean finalAnswer,
//			@RequestParam String jobPrefix) {
//		return interviewService.answer(interviewScheduleId, conversationHistory, finalAnswer, jobPrefix);
//	}
	
	@PostMapping("/answer")
	@PreAuthorize("hasAuthority('INTERVIEW_ANSWER')")
	public String answerQuestion(@RequestParam Long interviewScheduleId,
	                             @RequestParam String answer,
	                             @RequestParam(required = false) boolean finalAnswer,
	                             @RequestParam String jobPrefix,
	                             @RequestParam(required = false) String codeContent,
	                             @RequestParam(required = false) String codeLanguage) {
	    return interviewService.answer(interviewScheduleId, answer, finalAnswer, jobPrefix, codeContent, codeLanguage);
	}

	@PostMapping(value = "/voice-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('INTERVIEW_ANSWER')")
	public String convertVoiceToText(@RequestParam("file") MultipartFile file) throws Exception {

		byte[] audioBytes = file.getBytes();
		if (audioBytes == null || audioBytes.length == 0) {
			throw new IllegalArgumentException("Audio file is empty");
		}

		return speechToTextService.transcribeAudio(audioBytes, file.getOriginalFilename());
	}

	@PostMapping(value = "/{interviewScheduleId}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('INTERVIEW_ANSWER')")
	public String uploadInterviewVideo(@PathVariable Long interviewScheduleId,
			@RequestParam("file") MultipartFile videoFile) {
		return interviewService.storeRecording(interviewScheduleId, videoFile);
	}

	@PostMapping(value = "/{interviewScheduleId}/screen-recording", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('INTERVIEW_ANSWER')")
	public String uploadScreenRecording(@PathVariable Long interviewScheduleId,
			@RequestParam("file") MultipartFile screenFile) {
		return interviewService.storeScreenRecording(interviewScheduleId, screenFile);
	}

	// ==================== Voice Interview Endpoints ====================

	@PostMapping("/voice/start")
	@PreAuthorize("hasAuthority('INTERVIEW_START')")
	public ResponseEntity<VoiceStartResponse> startVoiceInterview(@RequestBody StartInterviewRequest req) {
		VoiceStartResponse response = voiceInterviewService.startVoiceInterview(req.getJobPrefix(), req.getEmail());
		return ResponseEntity.ok(response);
	}

	@PostMapping("/voice/{id}/end")
	@PreAuthorize("hasAuthority('INTERVIEW_ANSWER')")
	public ResponseEntity<?> endVoiceInterview(@PathVariable Long id) {
		voiceInterviewService.endVoiceInterview(id);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/voice/{id}/status")
	@PreAuthorize("hasAuthority('INTERVIEW_START')")
	public ResponseEntity<VoiceSessionStatus> getVoiceInterviewStatus(@PathVariable Long id) {
		return ResponseEntity.ok(voiceInterviewService.getSessionStatus(id));
	}

	@GetMapping("/voice/{id}/evaluation")
	@PreAuthorize("hasAuthority('INTERVIEW_START')")
	public ResponseEntity<VoiceEvaluationResult> getVoiceEvaluation(@PathVariable Long id) {
		return ResponseEntity.ok(voiceInterviewService.getEvaluation(id));
	}

	// Item 4: Resume endpoint
	@GetMapping("/voice/{scheduleId}/resume")
	@PreAuthorize("hasAuthority('INTERVIEW_START')")
	public ResponseEntity<ResumeResponse> resumeVoiceInterview(@PathVariable Long scheduleId) {
		return ResponseEntity.ok(voiceInterviewService.resumeInterview(scheduleId));
	}

	// Item 15/16: Get proctoring events for a schedule
	@GetMapping("/{scheduleId}/proctoring-events")
	@PreAuthorize("hasAuthority('INTERVIEW_ASSIGN')")
	public ResponseEntity<List<ProctoringEvent>> getProctoringEvents(@PathVariable Long scheduleId) {
		return ResponseEntity.ok(proctoringEventRepository.findByScheduleIdOrderByTimestampAsc(scheduleId));
	}

	// Item 16: Get full conversation transcript for a schedule
	@GetMapping("/{scheduleId}/conversation")
	@PreAuthorize("hasAuthority('INTERVIEW_ASSIGN')")
	public ResponseEntity<List<VoiceConversationEntry>> getConversation(@PathVariable Long scheduleId) {
		return ResponseEntity.ok(voiceConversationEntryRepository.findByInterviewScheduleIdOrderByTimestampAsc(scheduleId));
	}

	// Item 16: Get interview stats
	@GetMapping("/stats")
	@PreAuthorize("hasAuthority('INTERVIEW_ASSIGN')")
	public ResponseEntity<InterviewStatsDTO> getInterviewStats(@RequestParam(required = false) String jobPrefix) {
		List<CandidateInterviewSchedule> all = interviewService.getResults(jobPrefix);

		long total = all.size();
		long completed = all.stream().filter(s -> s.getAttemptStatus() == AttemptStatus.COMPLETED).count();
		long passed = all.stream().filter(s -> s.getInterviewResult() == InterviewResult.PASSED).count();
		double passRate = completed > 0 ? (double) passed / completed * 100 : 0;

		double avgScore = all.stream()
				.filter(s -> s.getEvaluationJson() != null && !s.getEvaluationJson().isBlank())
				.mapToDouble(s -> {
					try {
						// Extract overallScore from JSON
						String json = s.getEvaluationJson();
						int idx = json.indexOf("\"overallScore\"");
						if (idx < 0) return 0;
						String after = json.substring(idx + 15);
						// find the number
						StringBuilder num = new StringBuilder();
						for (char c : after.toCharArray()) {
							if (Character.isDigit(c) || c == '.') num.append(c);
							else if (num.length() > 0) break;
						}
						return num.length() > 0 ? Double.parseDouble(num.toString()) : 0;
					} catch (Exception e) {
						return 0;
					}
				})
				.filter(score -> score > 0)
				.average()
				.orElse(0);

		double avgDuration = all.stream()
				.filter(s -> s.getStartedAt() != null && s.getEndedAt() != null)
				.mapToDouble(s -> Duration.between(s.getStartedAt(), s.getEndedAt()).toMinutes())
				.average()
				.orElse(0);

		return ResponseEntity.ok(InterviewStatsDTO.builder()
				.totalInterviews(total)
				.passRate(Math.round(passRate * 10.0) / 10.0)
				.avgScore(Math.round(avgScore * 10.0) / 10.0)
				.avgDurationMinutes(Math.round(avgDuration * 10.0) / 10.0)
				.build());
	}
	
	@PostMapping("/verification-session")
	public ResponseEntity<?> createVerificationSession() {

	    String sessionId = roomVerificationService.createSession();

	    return ResponseEntity.ok(
	            Map.of("sessionId", sessionId)
	    );	
	}

	@PostMapping(value = "/verify-room", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> verifyRoom(
	        @RequestParam String sessionId,
	        @RequestParam MultipartFile image
	) {

	    roomVerificationService.verifyRoom(sessionId, image);

	    return ResponseEntity.ok().build();
	}

	@GetMapping("/verification-status")
	public ResponseEntity<?> verificationStatus(@RequestParam String sessionId) {

	    RoomVerificationSession session =
	            roomVerificationService.getStatus(sessionId);

	    return ResponseEntity.ok(
	            Map.of(
	                    "status", session.getStatus(),
	                    "reason", session.getReason()
	            )
	    );
	}
	
	@PostMapping(value = "/upload-interview-questions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('INTERVIEW_ASSIGN')")
	public ResponseEntity<?> uploadInterviewQuestions(
	        @RequestParam String jobPrefix,
	        @RequestParam("file") MultipartFile file) {

	    interviewQuestionsService.uploadInterviewQuestions(jobPrefix, file);

	    return ResponseEntity.ok(
	            Map.of("message", "Interview questions uploaded successfully")
	    );
	}

	@GetMapping("/questions")
	@PreAuthorize("hasAuthority('INTERVIEW_START')")
	public ResponseEntity<?> getInterviewQuestions(@RequestParam String jobPrefix) {

	    String data = interviewQuestionsService.fetchInterviewQuestions(jobPrefix);

	    return ResponseEntity.ok(data);
	}
}
