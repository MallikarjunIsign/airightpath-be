package com.rightpath.service.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rightpath.dto.JobPromptRequest;
import com.rightpath.entity.JobPost;
import com.rightpath.entity.JobPrompt;
import com.rightpath.enums.PromptStage;
import com.rightpath.enums.PromptType;
import com.rightpath.repository.JobPostRepository;
import com.rightpath.repository.JobPromptRepository;
import com.rightpath.service.JobPromptService;

import lombok.RequiredArgsConstructor;

@Service
public class JobPromptServiceImpl implements JobPromptService {

	private static final Logger log = LoggerFactory.getLogger(JobPromptServiceImpl.class);

	private final JobPromptRepository jobPromptRepository;
	private final JobPostRepository jobPostRepository;

	@Value("${interview.prompts.system:}")
	private String fallbackSystemPrompt;

	public JobPromptServiceImpl(JobPromptRepository jobPromptRepository, JobPostRepository jobPostRepository) {
		this.jobPromptRepository = jobPromptRepository;
		this.jobPostRepository = jobPostRepository;
	}

	@Override
	public JobPrompt saveJobPrompt(JobPromptRequest request) {

		JobPost jobPost = jobPostRepository.findByJobPrefix(request.getJobPrefix())
				.orElseThrow(() -> new RuntimeException("Invalid job prefix"));

		// Upsert: update existing prompt or create new one
		JobPrompt jobPrompt = jobPromptRepository
				.findByJobPrefixAndPromptTypeAndPromptStage(
						request.getJobPrefix(), request.getPromptType(), request.getPromptStage())
				.map(existing -> {
					existing.setPrompt(request.getPrompt());
					return existing;
				})
				.orElse(JobPrompt.builder()
						.jobPrefix(request.getJobPrefix())
						.promptType(request.getPromptType())
						.promptStage(request.getPromptStage())
						.prompt(request.getPrompt())
						.jobPost(jobPost)
						.build());

		return jobPromptRepository.save(jobPrompt);
	}

	@Override
	public List<JobPrompt> getPromptsByJobPrefix(String jobPrefix) {
		return jobPromptRepository.findAllByJobPrefix(jobPrefix);
	}

	@Override
	public String getPrompt(String jobPrefix, PromptType type, PromptStage stage) {
		return jobPromptRepository.findByJobPrefixAndPromptTypeAndPromptStage(jobPrefix, type, stage)
				.map(JobPrompt::getPrompt)
				.orElseGet(() -> {
					// Fallback to properties file for INTERVIEW/START prompts
					if (type == PromptType.INTERVIEW && stage == PromptStage.START
							&& fallbackSystemPrompt != null && !fallbackSystemPrompt.isBlank()) {
						log.warn("No DB prompt for jobPrefix={}, type={}, stage={}. Using fallback from properties.",
								jobPrefix, type, stage);
						return fallbackSystemPrompt;
					}
					throw new IllegalStateException(
							"Prompt not found for jobPrefix=" + jobPrefix + ", type=" + type + ", stage=" + stage);
				});
	}

	@Override
	public String buildStartPrompt(String jobPrefix, String resumeSummary) {
		String template = getPrompt(jobPrefix, PromptType.INTERVIEW, PromptStage.START);
		return template.replace("{{resumeSummary}}", resumeSummary == null ? "" : resumeSummary);
	}

	@Override
	public String buildSummaryPrompt(String jobPrefix, String transcript) {
		String template = getPrompt(jobPrefix, PromptType.INTERVIEW, PromptStage.SUMMARY);
		return template.replace("{{transcript}}", transcript);
	}

}
