package com.rightpath.util;

import static java.util.Objects.requireNonNull;

import org.springframework.stereotype.Service;

import com.rightpath.config.InterviewPromptProperties;

@Service
public class PromptService {

	private final InterviewPromptProperties props;

	public PromptService(InterviewPromptProperties props) {
		this.props = props;
	}

	public String getSystem() {
		return requireNonNull(props.getSystem(), "System prompt missing");
	}

	public String buildStartPrompt(String resumeSummary) {
		return props.getStart().replace("{{resumeSummary}}", resumeSummary);
	}

	public String buildSummaryPrompt(String transcript) {
		return props.getSummary().replace("{{transcript}}", transcript);
	}
}
