package com.rightpath.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CodeSubmissionResponseDTO {
	private Long id;
	private String language;
	private String script;
	private List<TestCaseDTO> testResults;
	private LocalDateTime createdAt;
	private String userEmail;
	private String questionId; 
	private Boolean passed;

}
