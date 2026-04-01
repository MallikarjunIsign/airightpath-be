package com.rightpath.service;

import java.util.List;

import com.rightpath.dto.JobPromptRequest;
import com.rightpath.entity.JobPrompt;
import com.rightpath.enums.PromptStage;
import com.rightpath.enums.PromptType;

public interface JobPromptService {

	JobPrompt saveJobPrompt(JobPromptRequest request);

	List<JobPrompt> getPromptsByJobPrefix(String jobPrefix);

	String buildStartPrompt(String jobPrefix, String resumeSummary);

	String buildSummaryPrompt(String jobPrefix, String transcript);

	String getPrompt(String jobPrefix, PromptType type, PromptStage stage);

}
