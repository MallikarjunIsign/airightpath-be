package com.rightpath.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.rightpath.enums.ApplicationStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobApplicationForCandidate {
	
	 @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;

	
	    private String lastName;
	   
	    private String firstName;
	    private String experience;
	    private String address;
	    private String jobRole;
	    private String resumeFileName;
	    private String mobileNumber;
	    

	    @Lob
	    private byte[] resumeData;
	    private double matchPercent; 
	    private String contentType;
	     	

	    @ManyToOne(fetch = FetchType.LAZY)
	    @JoinColumn(name = "user_email", referencedColumnName = "email")
	    private Users user; 
	    
	    
	    @ManyToOne(fetch = FetchType.LAZY)
	    @JoinColumn(name = "jobPrefix")
	    private JobPost jobPost;
	    
	    @Enumerated(EnumType.STRING)
	    private ApplicationStatus status;
	    
	    private String confirmationStatus; 
	    private String acknowledgedStatus;
	    private String reconfirmationStatus;
	    private String examLinkStatus;
	    private String examCompletedStatus;
	    private String rejectionStatus;
	    
	    private LocalDate examDate;
	    private LocalTime examTime;
	    private String writtenTestStatus;
	    private String interview;

	    private LocalDateTime createdAt;
	    private LocalDateTime updatedAt;

	    @PrePersist
	    protected void onCreate() {
	        this.createdAt = LocalDateTime.now();
	        this.updatedAt = LocalDateTime.now();
	    }

	    @PreUpdate
	    protected void onUpdate() {
	        this.updatedAt = LocalDateTime.now();
	    }
}