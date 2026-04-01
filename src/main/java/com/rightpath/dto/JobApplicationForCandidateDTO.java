package com.rightpath.dto;

import org.springframework.web.multipart.MultipartFile;

import com.drew.lang.annotations.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rightpath.entity.JobApplicationForCandidate;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobApplicationForCandidateDTO {
	
	   private Long id;
	  
	  @NotBlank
	    private String email;  // We collect email to map to Users entity

	    @NotBlank
	    private String lastName;
	    @NotBlank
	    private String firstName;

	    @NotBlank
	    private String experience;

	    @NotBlank
	    private String address;

	    @NotBlank
	    private String jobRole;

	    @NotNull
	    @JsonIgnore
	    private MultipartFile resume;
	    
	    
	    private String resumeFileName;
	    private String contentType;
	    private String userEmail;
	    private String mobileNumber;
	    
	    private String jobPrefix; 
	    
	    private double matchPercent; 
	    private String status; 
	    
	    
	    private String confirmationStatus; 
	    private String acknowledgedStatus;
	    private String reconfirmationStatus;
	    private String examLinkStatus;
	    private String examCompletedStatus; 
	    private String rejectionStatus;
	    private String writtenTestStatus;
	    private String interview;
	    private String interviewStatus;
	    private String jobTitle;
	    private String companyName;
	    private String applicationDeadline;

	    
	    public JobApplicationForCandidateDTO(JobApplicationForCandidate entity) {
	        this.id = entity.getId();
	        this.firstName = entity.getFirstName();
	        this.lastName = entity.getLastName();
	        this.experience = entity.getExperience();
	        this.address = entity.getAddress();
	        this.jobRole = entity.getJobRole();
	        this.resumeFileName = entity.getResumeFileName();
	        this.contentType = entity.getContentType();
	        this.userEmail = entity.getUser().getEmail();
	        this.jobPrefix = entity.getJobPost().getJobPrefix();
	        this.status = entity.getStatus() != null ? entity.getStatus().name() : null;
	        this.confirmationStatus = entity.getConfirmationStatus();
	        this.acknowledgedStatus = entity.getAcknowledgedStatus();
	        this.reconfirmationStatus = entity.getReconfirmationStatus();
	        this.examLinkStatus = entity.getExamLinkStatus();
	        this.examCompletedStatus = entity.getExamCompletedStatus();
	        this.rejectionStatus = entity.getRejectionStatus();
	        this.writtenTestStatus = entity.getWrittenTestStatus();
	        this.interview = entity.getInterview();

	    }

}