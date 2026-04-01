package com.rightpath.dto;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartInterviewRequest {
	private String email;
	@Column(nullable = false)
	private String jobPrefix;
	private String resumeSummary;
}