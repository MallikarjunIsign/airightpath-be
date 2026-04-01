package com.rightpath.dto;

import java.time.LocalDateTime;

import com.rightpath.entity.CandidateInterviewSchedule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CandidateInterviewScheduleDTO {

	private Long id;
	private String jobPrefix;
	private String email;
	private String attemptStatus; // attempted | not_attempted
	private String interviewResult; // passed | failed | pending
	private LocalDateTime assignedAt;
	private LocalDateTime deadlineTime;
	private String recordReferences;
	private String summaryReferences;
	private int warningCount;
	private String evaluationJson;
	private LocalDateTime startedAt;
	private LocalDateTime endedAt;

	public CandidateInterviewScheduleDTO(CandidateInterviewSchedule entity) {
		this.id = entity.getId();
		this.jobPrefix = entity.getJobPrefix();
		this.email = entity.getEmail();
		this.attemptStatus = entity.getAttemptStatus().toString();
		this.interviewResult = entity.getInterviewResult().toString();
		this.assignedAt = entity.getAssignedAt();
		this.deadlineTime = entity.getDeadlineTime();
		this.recordReferences = entity.getRecordReferences();
		this.summaryReferences = entity.getSummaryReferences();
		this.warningCount = entity.getWarningCount();
		this.evaluationJson = entity.getEvaluationJson();
		this.startedAt = entity.getStartedAt();
		this.endedAt = entity.getEndedAt();
	}
}
