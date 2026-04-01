package com.rightpath.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerInterviewRequest {
	@NotNull
	private Long interviewScheduleId;
	@Column(nullable = false)
	private String jobPrefix;
	@NotNull
	private String sessionId;
	@NotNull
	private String email;
	private String lastQuestion;
	@NotNull
	private String answer;
	@NotNull
	private boolean finalAnswer;
}