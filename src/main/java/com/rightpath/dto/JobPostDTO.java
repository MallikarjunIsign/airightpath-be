package com.rightpath.dto;

import lombok.*;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Job posting payload, used for both create and update and returned by the
 * listing endpoints.
 *
 * <p>Constraints here are enforced on {@code POST /api/jobs/post} and
 * {@code PUT /api/jobs/post/{id}} alike, so an edit cannot introduce data a
 * create would have rejected.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPostDTO {

	/**
	 * Server-assigned identifier. Populated on read so clients can address a
	 * posting ({@code PUT /api/jobs/post/{id}}); ignored on write — the id comes
	 * from the path, and {@code POST} always assigns a fresh one.
	 */
	private Long id;

	@NotBlank(message = "Job title is mandatory")
	private String jobTitle;

	/**
	 * Immutable once the posting exists: applications, assessments, evaluation
	 * categories and the public apply link all key off it. On create this is the
	 * human part of the generated code ({@code FE-DEV} → {@code FE-DEV-005}); on
	 * update it must match the stored value (or be omitted), else the request is
	 * rejected with {@code JOB_PREFIX_IMMUTABLE}.
	 */
	private String jobPrefix;

	private String companyName;
	private String location;

	// Matches the column length; without this an over-long description fails in the
	// database as a 500 instead of a readable 400.
	@Size(max = 3000, message = "Job description must be at most 3000 characters")
	private String jobDescription;

	private String keySkills;
	private String experience;
	private String education;
	private String salaryRange;
	private String jobType;
	private String industry;
	private String department;
	private String role;

	@Min(value = 1, message = "Number of openings must be at least 1")
	private Integer numberOfOpenings;

	@Email(message = "Contact email must be a valid email address")
	private String contactEmail;

	private LocalDate applicationDeadline;
}
