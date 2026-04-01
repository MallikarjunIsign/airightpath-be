package com.rightpath.dto.voice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceEvaluationResult {

    private double overallScore;
    private String recommendation;
    private List<CategoryScore> categoryScores;
    private SpeechAnalysis speechAnalysis;
    private String summary;
    private List<String> strengths;
    private List<String> areasForImprovement;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryScore {
        private String category;
        private double score;
        private double weight;
        private String feedback;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpeechAnalysis {
        private double averageWordsPerMinute;
        private int totalFillerWords;
        private double confidenceScore;
        private String paceAssessment;
        private String articulationFeedback;
    }
}
