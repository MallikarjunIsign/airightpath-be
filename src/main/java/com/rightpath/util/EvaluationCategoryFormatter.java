package com.rightpath.util;

import java.util.List;

import org.springframework.stereotype.Component;

import com.rightpath.entity.EvaluationCategory;
import com.rightpath.repository.EvaluationCategoryRepository;

@Component
public class EvaluationCategoryFormatter {

    private final EvaluationCategoryRepository evaluationCategoryRepository;

    // Default categories used when none are configured for a job.
    //
    // Weights total 100. Programming and Logical Reasoning are scored in their
    // own right rather than folded into Technical Skills: the transcript now
    // carries the code the candidate wrote and what it printed when they ran
    // it, so how they write code is visible evidence and no longer a guess from
    // how they talk about it. A job can replace all of this from
    // /api/prompts/evaluation-categories.
    private static final List<DefaultCategory> DEFAULTS = List.of(
            new DefaultCategory("Technical Skills", 22, "Core technical knowledge and expertise"),
            new DefaultCategory("Programming", 18,
                    "Correctness, structure and quality of the code written during the interview"),
            new DefaultCategory("Logical Reasoning", 15,
                    "Sound reasoning, and whether conclusions follow from what was said"),
            new DefaultCategory("Communication", 15, "Clarity and effectiveness of communication"),
            new DefaultCategory("Problem Solving", 12, "Analytical thinking and approach to problems"),
            new DefaultCategory("Behavioral & Culture Fit", 10, "Values alignment and teamwork"),
            new DefaultCategory("Articulation & Confidence", 8, "Confidence, poise, and delivery")
    );

    public EvaluationCategoryFormatter(EvaluationCategoryRepository evaluationCategoryRepository) {
        this.evaluationCategoryRepository = evaluationCategoryRepository;
    }

    /**
     * Load categories from DB, falling back to defaults if none exist.
     */
    public List<EvaluationCategory> getCategories(String jobPrefix) {
        List<EvaluationCategory> categories = evaluationCategoryRepository.findAllByJobPrefix(jobPrefix);
        if (categories != null && !categories.isEmpty()) {
            return categories;
        }
        return DEFAULTS.stream()
                .map(d -> EvaluationCategory.builder()
                        .categoryName(d.name)
                        .weight(d.weight)
                        .description(d.description)
                        .build())
                .toList();
    }

    /**
     * Build a human-readable category section for the interview START system prompt.
     */
    public String buildStartPromptCategorySection(String jobPrefix) {
        List<EvaluationCategory> categories = getCategories(jobPrefix);

        StringBuilder sb = new StringBuilder();
        sb.append("## Evaluation Categories & Question Distribution\n");
        sb.append("Distribute your interview questions across these categories proportionally by weight.\n\n");

        for (EvaluationCategory cat : categories) {
            sb.append(String.format("- **%s** (Weight: %.0f%%): %s\n",
                    cat.getCategoryName(),
                    cat.getWeight(),
                    cat.getDescription() != null ? cat.getDescription() : ""));
        }

        sb.append("\nEnsure each category receives at least one question. ");
        sb.append("Higher-weighted categories should receive more questions and deeper follow-ups.");

        return sb.toString();
    }

    /**
     * Build the JSON category template for the evaluation END prompt.
     */
    public String buildEvaluationCategorySection(String jobPrefix) {
        List<EvaluationCategory> categories = getCategories(jobPrefix);

        StringBuilder sb = new StringBuilder("\"categoryScores\": [\n");
        for (int i = 0; i < categories.size(); i++) {
            EvaluationCategory cat = categories.get(i);
            double weightDecimal = cat.getWeight() / 100.0;
            sb.append(String.format(
                    "        {\"category\": \"%s\", \"score\": <0-10>, \"weight\": %.2f, \"feedback\": \"<specific feedback for %s>\"}",
                    cat.getCategoryName(), weightDecimal, cat.getCategoryName().toLowerCase()
            ));
            if (i < categories.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ]");
        return sb.toString();
    }

    private record DefaultCategory(String name, double weight, String description) {}
}
