package com.rightpath.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignAssessmentDto {
    private List<String> candidateEmails;
    private String uploadedBy;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime deadline;
    private MultipartFile aptitudeQuestionPaper;
    private MultipartFile codingQuestionPaper;
    private MultipartFile aptitudeAnswerKey;  // Only for aptitude
    private boolean adminAcceptance = false;
    private String adminComments;
    private String jobPrefix;
}