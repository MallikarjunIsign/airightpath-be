package com.rightpath.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.rightpath.dto.EvaluationCategorySaveRequest;
import com.rightpath.dto.JobPromptRequest;
import com.rightpath.entity.EvaluationCategory;
import com.rightpath.entity.JobPrompt;
import com.rightpath.repository.EvaluationCategoryRepository;
import com.rightpath.service.JobPromptService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class JobPromptController {

    private final JobPromptService jobPromptService;
    private final EvaluationCategoryRepository evaluationCategoryRepository;

    @GetMapping("/{prefix}")
    @PreAuthorize("hasAuthority('JOB_POST_CREATE')")
    public ResponseEntity<List<JobPrompt>> getPromptsByPrefix(@PathVariable String prefix) {
        List<JobPrompt> prompts = jobPromptService.getPromptsByJobPrefix(prefix);
        return ResponseEntity.ok(prompts);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('JOB_POST_CREATE')")
    public ResponseEntity<JobPrompt> savePrompt(
            @RequestBody JobPromptRequest request) {

        JobPrompt savedPrompt = jobPromptService.saveJobPrompt(request);
        return ResponseEntity.ok(savedPrompt);
    }

    @GetMapping("/evaluation-categories/{prefix}")
    @PreAuthorize("hasAuthority('JOB_POST_CREATE')")
    public ResponseEntity<List<EvaluationCategory>> getEvaluationCategories(@PathVariable String prefix) {
        List<EvaluationCategory> categories = evaluationCategoryRepository.findAllByJobPrefix(prefix);
        return ResponseEntity.ok(categories);
    }

    @PostMapping("/evaluation-categories")
    @PreAuthorize("hasAuthority('JOB_POST_CREATE')")
    @Transactional
    public ResponseEntity<List<EvaluationCategory>> saveEvaluationCategories(
            @RequestBody EvaluationCategorySaveRequest request) {

        String jobPrefix = request.getJobPrefix();
        List<EvaluationCategory> existing = evaluationCategoryRepository.findAllByJobPrefix(jobPrefix);

        // Index existing categories by name for O(1) lookup
        Map<String, EvaluationCategory> existingByName = existing.stream()
                .collect(Collectors.toMap(EvaluationCategory::getCategoryName, c -> c));

        // Track incoming category names to identify removals
        Set<String> incomingNames = request.getCategories().stream()
                .map(EvaluationCategorySaveRequest.CategoryItem::getCategoryName)
                .collect(Collectors.toSet());

        // Update existing or create new
        List<EvaluationCategory> toSave = new ArrayList<>();
        for (var item : request.getCategories()) {
            EvaluationCategory cat = existingByName.get(item.getCategoryName());
            if (cat != null) {
                // Update in-place — preserves ID and createdAt
                cat.setWeight(item.getWeight());
                cat.setDescription(item.getDescription());
                toSave.add(cat);
            } else {
                // New category
                toSave.add(EvaluationCategory.builder()
                        .jobPrefix(jobPrefix)
                        .categoryName(item.getCategoryName())
                        .weight(item.getWeight())
                        .description(item.getDescription())
                        .build());
            }
        }

        // Delete categories that were removed by the admin
        List<EvaluationCategory> toDelete = existing.stream()
                .filter(c -> !incomingNames.contains(c.getCategoryName()))
                .toList();
        if (!toDelete.isEmpty()) {
            evaluationCategoryRepository.deleteAll(toDelete);
            evaluationCategoryRepository.flush();
        }

        List<EvaluationCategory> saved = evaluationCategoryRepository.saveAll(toSave);
        return ResponseEntity.ok(saved);
    }
}
