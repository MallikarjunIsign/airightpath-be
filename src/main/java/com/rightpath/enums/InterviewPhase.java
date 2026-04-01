package com.rightpath.enums;

import lombok.Getter;

@Getter
public enum InterviewPhase {
    INTRODUCTION("Introduction", 3, 2, "Warm-up and introductions"),
    BACKGROUND("Background", 8, 3, "Resume and experience discussion"),
    TECHNICAL("Technical", 20, 6, "Role-specific technical questions"),
    PROBLEM_SOLVING("Problem Solving", 12, 3, "Scenario and system design"),
    BEHAVIORAL("Behavioral", 10, 3, "STAR format behavioral questions"),
    CLOSING("Closing", 5, 1, "Wrap-up and candidate questions");

    private final String displayName;
    private final int durationMinutes;
    private final int targetQuestions;
    private final String description;

    InterviewPhase(String displayName, int durationMinutes, int targetQuestions, String description) {
        this.displayName = displayName;
        this.durationMinutes = durationMinutes;
        this.targetQuestions = targetQuestions;
        this.description = description;
    }

    public InterviewPhase next() {
        InterviewPhase[] phases = values();
        int nextIndex = this.ordinal() + 1;
        if (nextIndex >= phases.length) {
            return null;
        }
        return phases[nextIndex];
    }

    public boolean isLast() {
        return this == CLOSING;
    }
}
