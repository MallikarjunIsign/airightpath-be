package com.rightpath.dto.voice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoiceAnswerRequest {

    private String transcript;
    private List<WordTimestamp> wordTimestamps;
    private boolean skipped;
    private String codeContent;
    private String codeLanguage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WordTimestamp {
        private String word;
        private double start;
        private double end;
    }
}
