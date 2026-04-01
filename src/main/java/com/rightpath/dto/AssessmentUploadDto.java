package com.rightpath.dto;

import java.time.LocalDateTime;

import org.springframework.web.multipart.MultipartFile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentUploadDto {
	private String assessmentType;
	private String candidateEmail;
	private String uploadedBy;
	private MultipartFile questionPaper;
	private MultipartFile answerKey;
	private LocalDateTime deadline;
	private boolean adminAcceptance = false; 
	private String adminComments; 

}
