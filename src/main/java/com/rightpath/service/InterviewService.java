package com.rightpath.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.rightpath.dto.StartInterviewResponse;
import com.rightpath.entity.CandidateInterviewSchedule;
import com.rightpath.enums.AttemptStatus;
import com.rightpath.enums.InterviewResult;

public interface InterviewService {

	CandidateInterviewSchedule assignInterview(String jobPrefix, String email, LocalDateTime assignedAt,
			LocalDateTime deadlineTime);

	List<CandidateInterviewSchedule> assignInterviewBulk(String jobPrefix, java.util.List<String> emails,
			LocalDateTime assignedAt, LocalDateTime deadlineTime, boolean sendEmail);

	List<CandidateInterviewSchedule> getActiveInterviewsByEmail(String email);

	String storeRecording(Long interviewScheduleId, MultipartFile videoFile);

	String storeScreenRecording(Long interviewScheduleId, MultipartFile screenFile);

	StartInterviewResponse start(String jobPrefix, String email, String resumeSummary);

//	String answer(Long interviewScheduleId, String conversationHistory, boolean finalAnswer, String jobPrefix);

	void markCompleted(Long interviewScheduleId, AttemptStatus attemptStatus, InterviewResult interviewResult,
			String summaryRef);

	List<CandidateInterviewSchedule> getResults(String jobPrefix);

	CandidateInterviewSchedule getResultDetail(Long id);

	String prepareQuestionsAndCreateSession(String jobPrefix, String email, Long scheduleId);

	String answer(Long interviewScheduleId, String answerText, boolean finalAnswer, String jobPrefix,
			String codeContent, String codeLanguage);

}