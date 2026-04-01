package com.rightpath.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeSubmission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
   
	private String language;

	@Column(length = 10000)
	private String script;

	@Column(name = "user_email")
	private String userEmail;

	@Column(name = "job_prefix")
	private String jobPrefix;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_email", referencedColumnName = "email", insertable = false, updatable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Users user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "job_prefix", referencedColumnName = "job_prefix", insertable = false, updatable = false)
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private JobPost jobPost;

	private String questionId;

	private LocalDateTime createdAt;
	private  Boolean passed;

	
	private boolean attempted;
	 @Column(columnDefinition = "TEXT")
	    private String answersJson;

	@OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TestResultEntity> testResults;

	
	private String assessmentId;



}