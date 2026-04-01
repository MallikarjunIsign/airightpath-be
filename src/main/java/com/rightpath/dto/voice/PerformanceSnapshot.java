package com.rightpath.dto.voice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PerformanceSnapshot {

    private int totalQuestionsAsked;
    private int totalSkips;
    private int consecutiveSkips;
    private double averageWordCount;
    private double averageConfidenceScore;
    private int consecutiveShortAnswers;
    private double skipRatio;
    private boolean earlyTerminationSuggested;
}
