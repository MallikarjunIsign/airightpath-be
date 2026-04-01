package com.rightpath.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodeSubmissionRequestDTO {
	private String language;

	private String script;
	private List<TestCaseDTO> testCases;
	private String customInput;
	private LocalDateTime createdAt;
	private String userEmail;
	 private String questionId; 
	 private String jobPrefix;
	 private String assessmentId;


}
