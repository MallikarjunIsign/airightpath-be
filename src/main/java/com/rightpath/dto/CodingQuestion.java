package com.rightpath.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodingQuestion {
    private String id;
    private String title;
    private String question;
    private String description;

    @JsonProperty("Difficulty")
    private String difficulty;

    private String sampleInput;
    private String sampleOutput;

    private List<TestCase> testCases;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TestCase {
        private String input;
        private String expectedOutput;
    }
}
