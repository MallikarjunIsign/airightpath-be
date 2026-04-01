package com.rightpath.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightpath.dto.voice.VoiceEvaluationResult;
import com.rightpath.entity.CandidateInterviewSchedule;
import com.rightpath.entity.VoiceConversationEntry;
import com.rightpath.enums.CompletionReason;
import com.rightpath.enums.ConversationRole;
import com.rightpath.enums.InterviewResult;
import com.rightpath.repository.CandidateInterviewScheduleRepository;
import com.rightpath.repository.JobPromptRepository;
import com.rightpath.repository.VoiceConversationEntryRepository;
import com.rightpath.util.EvaluationCategoryFormatter;
import com.rightpath.util.PromptPlaceholderResolver;

@Service
public class InterviewEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(InterviewEvaluationService.class);

    // recommendations considered passing vs failing
    private static final Set<String> PASS_RECOMMENDATIONS = Set.of("STRONG_HIRE", "HIRE", "LEAN_HIRE");
    private static final Set<String> FAIL_RECOMMENDATIONS = Set.of("LEAN_NO_HIRE", "NO_HIRE");

    private final OpenAiStreamingService openAiStreamingService;
    private final CandidateInterviewScheduleRepository scheduleRepo;
    private final VoiceConversationEntryRepository entryRepository;
    private final JobPromptService jobPromptService;
    private final EvaluationCategoryFormatter categoryFormatter;
    private final JobPromptRepository jobPromptRepository;
    private final PromptPlaceholderResolver placeholderResolver;
    private final ObjectMapper objectMapper;

    public InterviewEvaluationService(OpenAiStreamingService openAiStreamingService,
                                      CandidateInterviewScheduleRepository scheduleRepo,
                                      VoiceConversationEntryRepository entryRepository,
                                      JobPromptService jobPromptService,
                                      EvaluationCategoryFormatter categoryFormatter,
                                      JobPromptRepository jobPromptRepository,
                                      PromptPlaceholderResolver placeholderResolver) {
        this.openAiStreamingService = openAiStreamingService;
        this.scheduleRepo = scheduleRepo;
        this.entryRepository = entryRepository;
        this.jobPromptService = jobPromptService;
        this.categoryFormatter = categoryFormatter;
        this.jobPromptRepository = jobPromptRepository;
        this.placeholderResolver = placeholderResolver;
        this.objectMapper = new ObjectMapper();
    }

    @Async("transcriptionExecutor")
    public void triggerEvaluationAsync(Long scheduleId) {
        try {
            evaluateInterview(scheduleId);
            log.info("Async evaluation completed for schedule {}", scheduleId);
        } catch (Exception e) {
            log.error("Async evaluation failed for schedule {}", scheduleId, e);
        }
    }

    public VoiceEvaluationResult evaluateInterview(Long scheduleId) {
        CandidateInterviewSchedule schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));

        // Return cached evaluation if already generated
        if (schedule.getEvaluationJson() != null && !schedule.getEvaluationJson().isBlank()) {
            log.info("Returning cached evaluation for schedule {}", scheduleId);
            VoiceEvaluationResult cached = parseEvaluation(schedule.getEvaluationJson());
            List<VoiceConversationEntry> cachedEntries = entryRepository
                    .findByInterviewScheduleIdOrderByTimestampAsc(scheduleId);
            cached.setSpeechAnalysis(calculateSpeechAnalysis(cachedEntries));
            return cached;
        }

        List<VoiceConversationEntry> entries = entryRepository
                .findByInterviewScheduleIdOrderByTimestampAsc(scheduleId);

        // Build transcript
        StringBuilder transcript = new StringBuilder();
        transcript.append("Position: ").append(schedule.getJobPrefix()).append("\n");
        transcript.append("Candidate: ").append(schedule.getEmail()).append("\n\n");

        for (VoiceConversationEntry entry : entries) {
            String speaker = switch (entry.getRole()) {
                case INTERVIEWER -> schedule.getInterviewerName();
                case CANDIDATE -> "Candidate";
                case SYSTEM -> "System";
            };
            transcript.append(speaker).append(": ").append(entry.getContent()).append("\n\n");
        }

        // Build evaluation prompt for this job including categories & transcript
        String prompt = buildEvaluationPrompt(schedule.getJobPrefix(), transcript.toString(),
                schedule.getCompletionReason(), schedule.getTotalQuestionsAsked());

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "You are an expert interview evaluator. Always respond with valid JSON only."),
                Map.of("role", "user", "content", prompt)
        );

        String evaluationJson = openAiStreamingService.chatCompletion(messages);

        // Parse evaluation
        VoiceEvaluationResult result = parseEvaluation(evaluationJson);

        // Add speech analysis
        VoiceEvaluationResult.SpeechAnalysis speechAnalysis = calculateSpeechAnalysis(entries);
        result.setSpeechAnalysis(speechAnalysis);

        // Save evaluation JSON
        schedule.setEvaluationJson(evaluationJson);

        // B4: Set interviewResult based on recommendation
        String recommendation = result.getRecommendation();
        if (recommendation != null) {
            if (PASS_RECOMMENDATIONS.contains(recommendation)) {
                schedule.setInterviewResult(InterviewResult.PASSED);
            } else if (FAIL_RECOMMENDATIONS.contains(recommendation)) {
                schedule.setInterviewResult(InterviewResult.FAILED);
            }
        }

        scheduleRepo.save(schedule);

        log.info("Generated evaluation for schedule {}: overall score {}, result {}",
                scheduleId, result.getOverallScore(), schedule.getInterviewResult());

        return result;
    }

    private String buildEvaluationPrompt(String jobPrefix, String transcript,
                                         CompletionReason completionReason, int totalQuestionsAsked) {
        String categorySection = categoryFormatter.buildEvaluationCategorySection(jobPrefix);

        // Try to load custom SUMMARY prompt
        String customInstructions = "";
        try {
            var summaryPrompt = jobPromptRepository.findByJobPrefixAndPromptTypeAndPromptStage(
                    jobPrefix, com.rightpath.enums.PromptType.INTERVIEW, com.rightpath.enums.PromptStage.SUMMARY);
            if (summaryPrompt.isPresent()) {
                String resolved = placeholderResolver.resolveJobPlaceholders(summaryPrompt.get().getPrompt(), jobPrefix);
                customInstructions = "\n\nAdditional evaluation instructions:\n" + resolved;
            }
        } catch (Exception e) {
            log.debug("No custom summary prompt for jobPrefix {}", jobPrefix);
        }

        // Add early termination context if applicable
        String earlyTerminationContext = "";
        if (completionReason == CompletionReason.EARLY_TERMINATION_POOR_PERFORMANCE) {
            earlyTerminationContext = String.format("""

                IMPORTANT CONTEXT: This interview was ended early after %d questions due to consistently poor candidate performance \
                (frequent skips, very short answers, or low confidence). Evaluate strictly based on what was demonstrated. \
                The limited number of questions should be factored into your assessment — the candidate did not have the opportunity \
                to demonstrate further skills, but the early termination itself is a significant signal of poor performance.""",
                    totalQuestionsAsked);
        }

        return String.format("""
                You are an expert interview evaluator. Analyze the following interview transcript and provide a detailed evaluation.

                Return your evaluation as a JSON object with this exact structure:
                {
                    "overallScore": <number 0-10>,
                    "recommendation": "<STRONG_HIRE | HIRE | LEAN_HIRE | LEAN_NO_HIRE | NO_HIRE>",
                    "summary": "<2-3 sentence overall assessment>",
                    "strengths": ["<strength 1>", "<strength 2>", "<strength 3>"],
                    "areasForImprovement": ["<area 1>", "<area 2>", "<area 3>"],
                    %s
                }

                IMPORTANT: Return ONLY the JSON object, no additional text or markdown.
                IMPORTANT: Scores must be on a 0-10 scale (not 0-100).
                %s%s

                Interview Transcript:
                %s
                """, categorySection, customInstructions, earlyTerminationContext, transcript);
    }

    private VoiceEvaluationResult parseEvaluation(String json) {
        try {
            String cleanJson = json.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "");
            }

            JsonNode node = objectMapper.readTree(cleanJson);

            List<VoiceEvaluationResult.CategoryScore> categoryScores = new ArrayList<>();
            JsonNode scoresNode = node.path("categoryScores");
            if (scoresNode.isArray()) {
                for (JsonNode scoreNode : scoresNode) {
                    categoryScores.add(VoiceEvaluationResult.CategoryScore.builder()
                            .category(scoreNode.path("category").asText())
                            .score(scoreNode.path("score").asDouble())
                            .weight(scoreNode.path("weight").asDouble())
                            .feedback(scoreNode.path("feedback").asText())
                            .build());
                }
            }

            List<String> strengths = new ArrayList<>();
            if (node.path("strengths").isArray()) {
                node.path("strengths").forEach(s -> strengths.add(s.asText()));
            }

            List<String> areas = new ArrayList<>();
            if (node.path("areasForImprovement").isArray()) {
                node.path("areasForImprovement").forEach(a -> areas.add(a.asText()));
            }

            return VoiceEvaluationResult.builder()
                    .overallScore(node.path("overallScore").asDouble())
                    .recommendation(node.path("recommendation").asText())
                    .summary(node.path("summary").asText())
                    .strengths(strengths)
                    .areasForImprovement(areas)
                    .categoryScores(categoryScores)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing evaluation JSON", e);
            return VoiceEvaluationResult.builder()
                    .overallScore(0)
                    .recommendation("UNABLE_TO_EVALUATE")
                    .summary("Evaluation could not be completed due to a processing error.")
                    .strengths(List.of())
                    .areasForImprovement(List.of())
                    .categoryScores(List.of())
                    .build();
        }
    }

    private VoiceEvaluationResult.SpeechAnalysis calculateSpeechAnalysis(List<VoiceConversationEntry> entries) {
        List<VoiceConversationEntry> candidateEntries = entries.stream()
                .filter(e -> e.getRole() == ConversationRole.CANDIDATE)
                .toList();

        if (candidateEntries.isEmpty()) {
            return VoiceEvaluationResult.SpeechAnalysis.builder()
                    .averageWordsPerMinute(0)
                    .totalFillerWords(0)
                    .confidenceScore(0)
                    .paceAssessment("No speech data available")
                    .articulationFeedback("No speech data available")
                    .build();
        }

        double totalWpm = 0;
        int totalFillers = 0;
        double totalConfidence = 0;
        int count = 0;

        for (VoiceConversationEntry entry : candidateEntries) {
            if (entry.getWordsPerMinute() != null) {
                totalWpm += entry.getWordsPerMinute();
                count++;
            }
            if (entry.getFillerWordCount() != null) {
                totalFillers += entry.getFillerWordCount();
            }
            if (entry.getConfidenceScore() != null) {
                totalConfidence += entry.getConfidenceScore();
            }
        }

        double avgWpm = count > 0 ? totalWpm / count : 0;
        double avgConfidence = count > 0 ? totalConfidence / count : 0;

        String paceAssessment;
        if (avgWpm >= 120 && avgWpm <= 160) {
            paceAssessment = "Excellent pace - natural and easy to follow";
        } else if (avgWpm < 120) {
            paceAssessment = "Slightly slow pace - could benefit from more energy";
        } else {
            paceAssessment = "Fast pace - consider slowing down for clarity";
        }

        String articulationFeedback;
        if (totalFillers <= 5) {
            articulationFeedback = "Very articulate with minimal filler words";
        } else if (totalFillers <= 15) {
            articulationFeedback = "Generally articulate with moderate use of filler words";
        } else {
            articulationFeedback = "Frequent use of filler words - practice pausing instead of using fillers";
        }

        return VoiceEvaluationResult.SpeechAnalysis.builder()
                .averageWordsPerMinute(Math.round(avgWpm * 10.0) / 10.0)
                .totalFillerWords(totalFillers)
                .confidenceScore(Math.round(avgConfidence * 10.0) / 10.0)
                .paceAssessment(paceAssessment)
                .articulationFeedback(articulationFeedback)
                .build();
    }
}
