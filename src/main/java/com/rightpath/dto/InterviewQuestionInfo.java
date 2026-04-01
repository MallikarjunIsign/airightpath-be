package com.rightpath.dto;

import lombok.Data;

@Data
public class InterviewQuestionInfo {
    private final String text;
    private final String uniqueId;
    private final String level;
}