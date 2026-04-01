package com.rightpath.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Question {
    private int id;
    private String question;
    private Map<String, String> options;
    private String correctAnswer;

    @JsonProperty("category")
    private String category;

    @JsonProperty("Difficulty")
    private String difficulty;

    /**
     * Captures flat option fields ("A", "B", "C", "D") that OpenAI sometimes
     * returns as top-level keys instead of nested inside an "options" map.
     */
    @JsonAnySetter
    public void handleUnknownField(String key, Object value) {
        if (key.length() == 1 && Character.isUpperCase(key.charAt(0))) {
            if (this.options == null) {
                this.options = new LinkedHashMap<>();
            }
            this.options.put(key, String.valueOf(value));
        }
    }

    // Getters & setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
}

