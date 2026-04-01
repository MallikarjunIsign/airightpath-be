package com.rightpath.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPostDTO {

	private String jobTitle;

	private String jobPrefix;
	private String companyName;
	private String location;
	private String jobDescription;
	private String keySkills;
	private String experience;
	private String education;
	private String salaryRange;
	private String jobType;
	private String industry;
	private String department;
	private String role;
	private Integer numberOfOpenings;
	private String contactEmail;
	private LocalDate applicationDeadline;
}
