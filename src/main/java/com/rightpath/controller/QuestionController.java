package com.rightpath.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rightpath.dto.CodingQuestion;
import com.rightpath.dto.Question;
import com.rightpath.service.OpenAiService;

@RestController
@RequestMapping("/api")
public class QuestionController {

    private static final Logger log = LoggerFactory.getLogger(QuestionController.class);
    private final OpenAiService openAiService;

    public QuestionController(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

    @GetMapping("/generate-questions")
    @PreAuthorize("hasAuthority('QUESTION_GENERATE')")
    public List<Question> generateQuestions(@RequestParam String jobPrefix) {
        log.info("Received request to generate aptitude questions for jobPrefix={}", jobPrefix);
        long start = System.currentTimeMillis();
        try {
            List<Question> questions = openAiService.generateQuestions(jobPrefix);
            log.info("Aptitude questions generated successfully for jobPrefix={}, count={}, took={}ms",
                    jobPrefix, questions.size(), System.currentTimeMillis() - start);
            return questions;
        } catch (Exception e) {
            log.error("Failed to generate aptitude questions for jobPrefix={}, took={}ms, error={}",
                    jobPrefix, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    @GetMapping("/generate-coding-questions")
    @PreAuthorize("hasAuthority('CODING_QUESTION_GENERATE')")
    public List<CodingQuestion> generateCodingQuestions(@RequestParam String jobPrefix) {
        log.info("Received request to generate coding questions for jobPrefix={}", jobPrefix);
        long start = System.currentTimeMillis();
        try {
            List<CodingQuestion> questions = openAiService.generateCodingQuestions(jobPrefix);
            log.info("Coding questions generated successfully for jobPrefix={}, count={}, took={}ms",
                    jobPrefix, questions.size(), System.currentTimeMillis() - start);
            return questions;
        } catch (Exception e) {
            log.error("Failed to generate coding questions for jobPrefix={}, took={}ms, error={}",
                    jobPrefix, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }
}
