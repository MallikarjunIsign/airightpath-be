package com.rightpath.dto;

import com.rightpath.enums.PromptStage;
import com.rightpath.enums.PromptType;

import lombok.Data;

@Data
public class JobPromptRequest {

	private String jobPrefix;
	private PromptType promptType;
	private PromptStage promptStage;
	private String prompt;
}
