package com.rightpath.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rightpath.dto.voice.PerformanceSnapshot;
import com.rightpath.entity.CandidateInterviewSchedule;
import com.rightpath.entity.VoiceConversationEntry;
import com.rightpath.enums.ConversationRole;
import com.rightpath.enums.PromptStage;
import com.rightpath.enums.PromptType;
import com.rightpath.repository.VoiceConversationEntryRepository;
import com.rightpath.util.PromptPlaceholderResolver;

@Service
public class InterviewContextService {

    private static final Logger log = LoggerFactory.getLogger(InterviewContextService.class);

    private final VoiceConversationEntryRepository entryRepository;
    private final OpenAiStreamingService openAiStreamingService;
    private final JobPromptService jobPromptService;
    private final PromptPlaceholderResolver placeholderResolver;
    private final CandidatePerformanceAnalyzer performanceAnalyzer;

    @Value("${interview.context-window-size:4}")
    private int contextWindowSize;

    public InterviewContextService(VoiceConversationEntryRepository entryRepository,
                                   OpenAiStreamingService openAiStreamingService,
                                   JobPromptService jobPromptService,
                                   PromptPlaceholderResolver placeholderResolver,
                                   CandidatePerformanceAnalyzer performanceAnalyzer) {
        this.entryRepository = entryRepository;
        this.openAiStreamingService = openAiStreamingService;
        this.jobPromptService = jobPromptService;
        this.placeholderResolver = placeholderResolver;
        this.performanceAnalyzer = performanceAnalyzer;
    }

    /**
     * Build the message list for GPT, using rolling context window.
     * System prompt is fetched from DB (JobPrompt table).
     */
    public List<Map<String, String>> buildContextMessages(CandidateInterviewSchedule schedule, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        // System prompt from DB
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(schedule)));

        // Add running summary if exists
        if (schedule.getRunningSummary() != null && !schedule.getRunningSummary().isBlank()) {
            messages.add(Map.of("role", "system", "content",
                    "Summary of earlier conversation: " + schedule.getRunningSummary()));
        }

        // Inject performance guidance if candidate is struggling
        String performanceGuidance = buildPerformanceGuidance(schedule);
        if (performanceGuidance != null) {
            messages.add(Map.of("role", "system", "content", performanceGuidance));
        }

        // Last N exchanges verbatim
        List<VoiceConversationEntry> allEntries = entryRepository
                .findByInterviewScheduleIdOrderByTimestampAsc(schedule.getId());

        int totalEntries = allEntries.size();
        int startIndex = Math.max(0, totalEntries - (contextWindowSize * 2));
        List<VoiceConversationEntry> recentEntries = allEntries.subList(startIndex, totalEntries);

        for (VoiceConversationEntry entry : recentEntries) {
            String role = switch (entry.getRole()) {
                case INTERVIEWER -> "assistant";
                case CANDIDATE -> "user";
                case SYSTEM -> "system";
            };
            messages.add(Map.of("role", role, "content", entry.getContent()));
        }

        // Current user message
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(Map.of("role", "user", "content", userMessage));
        }

        return messages;
    }

    /**
     * Build an updated running summary after each Q&A exchange.
     * Returns the summary string, or null if not enough entries to summarize yet.
     * Caller is responsible for persisting the summary on the schedule entity.
     */
    public String updateRunningSummary(CandidateInterviewSchedule schedule) {
        List<VoiceConversationEntry> entries = entryRepository
                .findByInterviewScheduleIdOrderByTimestampAsc(schedule.getId());

        if (entries.size() <= contextWindowSize * 2) {
            return null;
        }

        int cutoff = entries.size() - (contextWindowSize * 2);
        StringBuilder conversationText = new StringBuilder();
        for (int i = 0; i < cutoff; i++) {
            VoiceConversationEntry entry = entries.get(i);
            conversationText.append(entry.getRole().name())
                    .append(": ")
                    .append(entry.getContent())
                    .append("\n\n");
        }

        String summaryPrompt = "Summarize the following interview conversation in 200 words or less. "
                + "Focus on: key topics discussed, candidate's main points, strengths shown, and areas probed. "
                + "Keep it factual and concise.\n\nConversation:\n" + conversationText;

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "You are a concise summarizer."),
                Map.of("role", "user", "content", summaryPrompt)
        );

        String summary = openAiStreamingService.chatCompletion(messages);
        log.debug("Built running summary for schedule {}: {} chars", schedule.getId(), summary.length());
        return summary;
    }

    /**
     * Build a supplementary system message when candidate performance is poor.
     * Returns null when performance is acceptable.
     */
    public String buildPerformanceGuidance(CandidateInterviewSchedule schedule) {
        PerformanceSnapshot snapshot = performanceAnalyzer.analyze(schedule);
        if (!snapshot.isEarlyTerminationSuggested()) {
            return null;
        }

        return String.format("""
                [PERFORMANCE CONTEXT — for your decision-making only, NEVER share with the candidate]
                Candidate performance data after %d questions:
                - Questions skipped: %d (%.0f%% skip rate)
                - Consecutive skips: %d
                - Average answer length: %.0f words
                - Average confidence score: %.0f/100
                - Consecutive short/skipped answers: %d

                Based on this data, the candidate appears to be struggling significantly.
                You MAY choose to professionally wrap up the interview early. If you decide to end it:
                - Say something like: "I believe I have a good understanding of your background. Thank you for your time today."
                - Include [INTERVIEW_COMPLETE] at the end of your message.
                - NEVER mention poor performance, low scores, or that you are ending early due to their answers.
                - Keep the tone warm and professional.

                You may also choose to continue if you believe the candidate might improve.""",
                snapshot.getTotalQuestionsAsked(),
                snapshot.getTotalSkips(),
                snapshot.getSkipRatio() * 100,
                snapshot.getConsecutiveSkips(),
                snapshot.getAverageWordCount(),
                snapshot.getAverageConfidenceScore(),
                snapshot.getConsecutiveShortAnswers());
    }

    /**
     * Build system prompt from DB with placeholder substitution.
     */
    private String buildSystemPrompt(CandidateInterviewSchedule schedule) {
        String template = jobPromptService.getPrompt(
                schedule.getJobPrefix(), PromptType.INTERVIEW, PromptStage.START);
        return placeholderResolver.resolveAllInterviewPlaceholders(
                template, schedule.getJobPrefix(), schedule.getEmail(), schedule.getInterviewerName());
    }

    /**
     * Build prompt for generating the first question.
     */
    public String buildFirstQuestionPrompt() {
        return "Begin the interview now.";
    }

    /**
     * Build prompt for generating the next question.
     * No phase transition logic — AI decides the flow.
     */
    public String buildNextQuestionPrompt(CandidateInterviewSchedule schedule, String candidateAnswer, boolean skipped) {
        return buildNextQuestionPrompt(schedule, candidateAnswer, skipped, null, null);
    }

    public String buildNextQuestionPrompt(CandidateInterviewSchedule schedule, String candidateAnswer, boolean skipped,
                                          String codeContent, String codeLanguage) {
        if (skipped) {
            return "The candidate did not respond to the previous question within the time limit. "
                    + "Briefly acknowledge this (e.g., 'No worries, let's move on.') and ask the next question.";
        }

        if (codeContent != null && !codeContent.isBlank()) {
            String lang = (codeLanguage != null && !codeLanguage.isBlank()) ? codeLanguage : "text";
            return String.format(
                    "The candidate answered verbally: \"%s\"\n\nThey also submitted the following code (%s):\n```%s\n%s\n```\n\n" +
                    "Evaluate the code for correctness, efficiency, and code quality. Then proceed with the next question.",
                    candidateAnswer, lang, lang, codeContent);
        }

        return String.format("The candidate just answered: \"%s\"", candidateAnswer);
    }
}
