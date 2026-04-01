package com.rightpath.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "interview.prompts")
public class InterviewPromptProperties {
	private String system;
	private String start;
	private String summary;

}
