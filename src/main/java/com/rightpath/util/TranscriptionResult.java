package com.rightpath.util;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionResult {

    private String text;
    private double duration;
    private List<Word> words;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Word {
        private String word;
        private double start;
        private double end;
    }
}
