package com.rightpath.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.rightpath.dto.JobPostDTO;
import com.rightpath.entity.JobApplicationForCandidate;
import com.rightpath.entity.JobPost;
import com.rightpath.entity.Users;
import com.rightpath.repository.JobApplicationForCandidateRepository;
import com.rightpath.repository.JobPostRepository;
import com.rightpath.repository.UsersRepository;
import com.rightpath.service.JobPostService;

@Service
public class JobPostServiceImpl implements JobPostService {

	@Autowired
	private JobPostRepository repository;
	@Autowired
	private UsersRepository usersRepository;
	@Autowired
	private JobApplicationForCandidateRepository jobApplicationRepository;

	public JobPost createJobPost(JobPostDTO dto) {
		Long lastId = repository.findTopByOrderByIdDesc().map(JobPost::getId).orElse(0L);

		String prefix = dto.getJobPrefix() != null ? dto.getJobPrefix().toUpperCase() : "JOB";
		String jobCode = String.format("%s-%03d", prefix, lastId + 1);

		JobPost jobPost = JobPost.builder().jobPrefix(jobCode).jobTitle(dto.getJobTitle())
				.companyName(dto.getCompanyName()).location(dto.getLocation()).jobDescription(dto.getJobDescription())
				.keySkills(dto.getKeySkills()).experience(dto.getExperience()).education(dto.getEducation())
				.salaryRange(dto.getSalaryRange()).jobType(dto.getJobType()).industry(dto.getIndustry())
				.department(dto.getDepartment()).role(dto.getRole()).numberOfOpenings(dto.getNumberOfOpenings())
				.contactEmail(dto.getContactEmail()).applicationDeadline(dto.getApplicationDeadline())
				.createdAt(LocalDate.now()).build();

		return repository.save(jobPost);
	}

	@Override
	public List<JobPostDTO> getAllJobPosts() {
		return repository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
	}

	@Override
	public JobPostDTO convertToDTO(JobPost post) {
		return JobPostDTO.builder().jobPrefix(post.getJobPrefix()).jobTitle(post.getJobTitle())
				.companyName(post.getCompanyName()).location(post.getLocation())
				.jobDescription(post.getJobDescription()).keySkills(post.getKeySkills())
				.experience(post.getExperience()).education(post.getEducation()).salaryRange(post.getSalaryRange())
				.jobType(post.getJobType()).industry(post.getIndustry()).department(post.getDepartment())
				.role(post.getRole()).numberOfOpenings(post.getNumberOfOpenings()).contactEmail(post.getContactEmail())
				.applicationDeadline(post.getApplicationDeadline()).build();
	}

	public String applyToJob(Long jobId, String userEmail) {
		JobPost jobPost = repository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));

		Users user = usersRepository.findById(userEmail).orElseThrow(() -> new RuntimeException("User not found"));

		// Check if user already applied via existing job applications
		boolean alreadyApplied = jobApplicationRepository
				.findByJobPost_JobPrefixAndUser_Email(jobPost.getJobPrefix(), userEmail)
				.isPresent();
		if (alreadyApplied) {
			return "User already applied for this job.";
		}

		JobApplicationForCandidate application = JobApplicationForCandidate.builder()
				.user(user)
				.jobPost(jobPost)
				.firstName(user.getFirstName())
				.lastName(user.getLastName())
				.build();
		jobApplicationRepository.save(application);
		return "Application submitted successfully.";
	}

	@Override
	public int getApplicationCount(Long jobId) {
		JobPost jobPost = repository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
		return jobApplicationRepository.findByJobPost(jobPost).size();
	}

	@Override
	public JobPostDTO findByJobPrefix(String jobPrefix) {
		// TODO Auto-generated method stub
		return null;
	}
	
	

	
}