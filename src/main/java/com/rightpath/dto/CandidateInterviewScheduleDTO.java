package com.rightpath.dto;

import java.time.LocalDate;
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
	/**
	 * Where the candidate's shared screen was stored.
	 *
	 * The upload has always worked and the column has always been written, but
	 * this field was missing from the DTO — so the recording sat in storage with
	 * no way for a reviewer to reach it. For a coding round it is the only
	 * evidence of how the answer was arrived at.
	 */
	private String screenRecordReferences;
	private String summaryReferences;
	private int warningCount;
	private String evaluationJson;
	private LocalDateTime startedAt;
	private LocalDateTime endedAt;

	/**
	 * Whether the AI's verdict should be looked at before it is acted on.
	 *
	 * <p>Read from the schedule's own column rather than out of
	 * {@link #evaluationJson}, so a list of fifty results does not deserialise
	 * fifty LONGTEXT blobs to find out which of them are marked.</p>
	 */
	private boolean needsHumanReview;

	/**
	 * The result a person imposed, or null where the AI's stands.
	 *
	 * <p>Kept beside {@link #interviewResult} rather than replacing it. A screen
	 * that shows only the final answer cannot say the machine was overruled,
	 * which is the part a reviewer opening it next actually needs to know.</p>
	 */
	private String overriddenResult;

	/** Who overturned it. Null where nobody did. */
	private String overriddenBy;
	   private LocalDate questionsFromDate;
	    private LocalDate questionsToDate;

	/**
	 * Which interview this was — {@code L2_TECHNICAL} or {@code L3_BEHAVIORAL}.
	 *
	 * <p>Always populated, including for schedules written before the column
	 * existed: those read as the default round rather than null, so a results
	 * screen filtering by round does not hide historic interviews.</p>
	 */
	private String round;

	/** Human label for the round, so clients need not map the enum. */
	private String roundLabel;

	public CandidateInterviewScheduleDTO(CandidateInterviewSchedule entity) {
		this.round = entity.getEffectiveRound().name();
		this.roundLabel = entity.getEffectiveRound().getDisplayName();
		this.id = entity.getId();
		this.jobPrefix = entity.getJobPrefix();
		this.email = entity.getEmail();
		this.attemptStatus = entity.getAttemptStatus().toString();
		this.interviewResult = entity.getInterviewResult().toString();
		this.assignedAt = entity.getAssignedAt();
		this.deadlineTime = entity.getDeadlineTime();
		this.recordReferences = entity.getRecordReferences();
		this.screenRecordReferences = entity.getScreenRecordReferences();
		this.summaryReferences = entity.getSummaryReferences();
		this.warningCount = entity.getWarningCount();
		this.evaluationJson = entity.getEvaluationJson();
		this.startedAt = entity.getStartedAt();
		this.endedAt = entity.getEndedAt();
		this.needsHumanReview = entity.isNeedsHumanReview();
	}

	/**
	 * The same DTO with a reviewer's decision attached.
	 *
	 * <p>A second constructor rather than a lookup inside the first: the entity
	 * does not know about its review, and having the DTO fetch one per row
	 * would put a query per result behind every list that renders one.</p>
	 */
	public CandidateInterviewScheduleDTO(CandidateInterviewSchedule entity,
			com.rightpath.entity.InterviewReview review) {
		this(entity);
		if (review != null && review.getOverriddenResult() != null) {
			this.overriddenResult = review.getOverriddenResult().name();
			this.overriddenBy = review.getReviewerEmail();
		}
	}
}
