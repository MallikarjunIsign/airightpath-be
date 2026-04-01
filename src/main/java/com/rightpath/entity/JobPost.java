package com.rightpath.entity;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	
	@Column(name = "job_prefix", unique = true)
	private String jobPrefix;
	private String jobTitle;
	private String companyName;
	private String location;

	@Column(length = 3000)
	private String jobDescription;

	private String keySkills;
	private String experience;
	private String education;
	private String salaryRange;

	private String jobType; // e.g. Full-time
	private String industry;
	private String department;
	private String role;
	private Integer numberOfOpenings;

	private String contactEmail;
	private LocalDate applicationDeadline;

	private LocalDate createdAt;

	@OneToMany(mappedBy = "jobPost", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
	private Set<JobApplicationForCandidate> jobApplications = new HashSet<>();

	
}
