package com.rightpath.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.rightpath.dto.StartInterviewResponse;
import com.rightpath.entity.CandidateInterviewSchedule;
import com.rightpath.enums.AttemptStatus;
import com.rightpath.enums.InterviewResult;

public interface InterviewService {

//	CandidateInterviewSchedule assignInterview(String jobPrefix, String email, LocalDateTime assignedAt,
//			LocalDateTime deadlineTime);
//
//	List<CandidateInterviewSchedule> assignInterviewBulk(String jobPrefix, java.util.List<String> emails,
//			LocalDateTime assignedAt, LocalDateTime deadlineTime, boolean sendEmail);

	List<CandidateInterviewSchedule> getActiveInterviewsByEmail(String email);

	String storeRecording(Long interviewScheduleId, MultipartFile videoFile);

	String storeScreenRecording(Long interviewScheduleId, MultipartFile screenFile);

	StartInterviewResponse start(String jobPrefix, String email, String resumeSummary);

//	String answer(Long interviewScheduleId, String conversationHistory, boolean finalAnswer, String jobPrefix);

	void markCompleted(Long interviewScheduleId, AttemptStatus attemptStatus, InterviewResult interviewResult,
			String summaryRef);

	/**
	 * Every interview on a job, or every interview there is when no job is given.
	 *
	 * @deprecated retained for callers that genuinely want all rounds; prefer the
	 *             overload, which makes the round explicit rather than implied.
	 */
	@Deprecated
	List<CandidateInterviewSchedule> getResults(String jobPrefix);

	/**
	 * Interviews on a job, optionally narrowed to one round.
	 *
	 * <p>Matching is on the <em>effective</em> round, so schedules written before
	 * the column existed count as {@link com.rightpath.enums.InterviewRound#DEFAULT}
	 * — the same reading the DTO reports. Filtering on the stored column instead
	 * would make historic interviews vanish from both rounds.</p>
	 *
	 * @param round the round to keep, or null for all rounds
	 */
	List<CandidateInterviewSchedule> getResults(String jobPrefix, com.rightpath.enums.InterviewRound round);

	CandidateInterviewSchedule getResultDetail(Long id);

	String prepareQuestionsAndCreateSession(String jobPrefix, String email, Long scheduleId);

	/**
	 * @deprecated superseded by the overload taking {@code codeOutput}; a coding
	 *             answer graded without its run output is graded on source alone.
	 */
	@Deprecated
	String answer(Long interviewScheduleId, String answerText, boolean finalAnswer, String jobPrefix,
			String codeContent, String codeLanguage);

	/**
	 * Processes one candidate answer and returns the interviewer's next message.
	 *
	 * @param codeOutput what the candidate's code printed when they ran it, or
	 *                   null if they never did. Passed on to the model so a
	 *                   coding answer is judged on behaviour, not only on source.
	 */
	String answer(Long interviewScheduleId, String answerText, boolean finalAnswer, String jobPrefix,
			String codeContent, String codeLanguage, String codeOutput);

//	String prepareQuestionsAndCreateSession(String jobPrefix, String email, Long scheduleId, Long fromDate,
//			Long toDate);

//	List<CandidateInterviewSchedule> assignInterviewBulk(String jobPrefix, List<String> emails,
//			LocalDateTime assignedAt, LocalDateTime deadlineTime, boolean sendEmail, LocalDateTime questionsFromDate,
//			LocalDateTime questionsToDate);
//
//	CandidateInterviewSchedule assignInterview(String jobPrefix, String email, LocalDateTime assignedAt,
//			LocalDateTime deadlineTime, LocalDateTime questionsFromDate, LocalDateTime questionsToDate);
	
	  CandidateInterviewSchedule assignInterview(String jobPrefix, String email, LocalDateTime assignedAt,
	            LocalDateTime deadlineTime, LocalDate questionsFromDate, LocalDate questionsToDate);
	    
	    /**
	     * @deprecated superseded by the overload taking an
	     *             {@link com.rightpath.enums.InterviewRound}; this one books
	     *             the default round.
	     */
	    @Deprecated
	    List<CandidateInterviewSchedule> assignInterviewBulk(String jobPrefix, List<String> emails,
	            LocalDateTime assignedAt, LocalDateTime deadlineTime, boolean sendEmail,
	            LocalDate questionsFromDate, LocalDate questionsToDate);

	    /**
	     * Books one interview round for each of the given candidates.
	     *
	     * @param round which interview to book; null means the default round, so a
	     *              caller written before rounds existed keeps its old behaviour
	     */
	    List<CandidateInterviewSchedule> assignInterviewBulk(String jobPrefix, List<String> emails,
	            LocalDateTime assignedAt, LocalDateTime deadlineTime, boolean sendEmail,
	            LocalDate questionsFromDate, LocalDate questionsToDate,
	            com.rightpath.enums.InterviewRound round);

}