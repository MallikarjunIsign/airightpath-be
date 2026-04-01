package com.rightpath.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.rightpath.enums.AttemptStatus;
import com.rightpath.enums.CompletionReason;
import com.rightpath.enums.InterviewPhase;
import com.rightpath.enums.InterviewResult;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CandidateInterviewSchedule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "job_prefix")
	private String jobPrefix;

	private String email;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "email", referencedColumnName = "email", insertable = false, updatable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Users user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "job_prefix", referencedColumnName = "job_prefix", insertable = false, updatable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private JobPost jobPost;

	@Enumerated(EnumType.STRING)
	private AttemptStatus attemptStatus;

	@Enumerated(EnumType.STRING)
	private InterviewResult interviewResult;

	@Column(columnDefinition = "TEXT")
	private String recordReferences;

	@Column(columnDefinition = "TEXT")
	private String screenRecordReferences;

	@Column(name = "summery_references", columnDefinition = "TEXT")
	private String summaryReferences;

	private LocalDateTime assignedAt;

	private LocalDateTime deadlineTime;

	// Keep column for DB compatibility but no longer driven by backend logic
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private InterviewPhase currentPhase = InterviewPhase.INTRODUCTION;

	@Builder.Default
	private int difficultyLevel = 2;

	@Builder.Default
	private int questionsAskedInPhase = 0;

	@Builder.Default
	private int totalQuestionsAsked = 0;

	@Column(columnDefinition = "TEXT")
	private String runningSummary;

	@Builder.Default
	private int warningCount = 0;

	@Column(columnDefinition = "LONGTEXT")
	private String evaluationJson;

	@Column(columnDefinition = "VARCHAR(255) DEFAULT 'Sarah'")
	@Builder.Default
	private String interviewerName = "Sarah";

	public String getInterviewerName() {
		return interviewerName != null && !interviewerName.isBlank() ? interviewerName : "Sarah";
	}

	@Enumerated(EnumType.STRING)
	private CompletionReason completionReason;

	private LocalDateTime startedAt;
	private LocalDateTime endedAt;

	@OneToMany(mappedBy = "interviewSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private List<VoiceConversationEntry> conversationEntries = new ArrayList<>();

	@OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private List<ProctoringEvent> proctoringEvents = new ArrayList<>();

	public void incrementQuestionsAsked() {
		this.totalQuestionsAsked++;
	}

	public void addWarning() {
		this.warningCount++;
	}
}
