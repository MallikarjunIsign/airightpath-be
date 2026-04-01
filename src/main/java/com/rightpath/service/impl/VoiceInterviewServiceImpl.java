package com.rightpath.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.rightpath.dto.voice.PerformanceSnapshot;
import com.rightpath.dto.voice.ResumeResponse;
import com.rightpath.dto.voice.VoiceAnswerRequest;
import com.rightpath.dto.voice.VoiceEvaluationResult;
import com.rightpath.dto.voice.VoiceSessionStatus;
import com.rightpath.dto.voice.VoiceStartResponse;
import com.rightpath.entity.CandidateInterviewSchedule;
import com.rightpath.entity.VoiceConversationEntry;
import com.rightpath.enums.AttemptStatus;
import com.rightpath.enums.CompletionReason;
import com.rightpath.enums.ConversationRole;
import com.rightpath.enums.InterviewResult;
import com.rightpath.repository.CandidateInterviewScheduleRepository;
import com.rightpath.repository.VoiceConversationEntryRepository;
import com.rightpath.service.CandidatePerformanceAnalyzer;
import com.rightpath.service.InterviewContextService;
import com.rightpath.service.InterviewEvaluationService;
import com.rightpath.service.InterviewService;
import com.rightpath.service.OpenAiStreamingService;
import com.rightpath.service.TextToSpeechService;
import com.rightpath.service.ToneAnalysisService;
import com.rightpath.service.VoiceInterviewService;

@Service
public class VoiceInterviewServiceImpl implements VoiceInterviewService {
	
	@Autowired
	private InterviewService interviewService;

    private static final Logger log = LoggerFactory.getLogger(VoiceInterviewServiceImpl.class);

    private static final String INTERVIEW_COMPLETE_MARKER = "[INTERVIEW_COMPLETE]";
    private static final java.util.regex.Pattern QUESTION_TYPE_TAG_PATTERN =
            java.util.regex.Pattern.compile("\\[(CODING|THEORY|NON-TECH)]\\s*", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final List<String> FILLERS = List.of(
            "That's a great point, let me think about what I'd like to explore next...",
            "Interesting, I appreciate you sharing that. Let me consider my follow-up...",
            "Thank you for that detailed response. Give me a moment...",
            "That's really helpful context. Let me think about the best direction to go...",
            "I see, that's quite insightful. Let me formulate my next question...",
            "Great, I appreciate the thoroughness of your answer. One moment...",
            "That makes sense. Let me think about what would be most valuable to discuss next...",
            "Wonderful. Let me take a moment to consider the best follow-up..."
    );

    private static final Random random = new Random();

    private final CandidateInterviewScheduleRepository scheduleRepo;
    private final VoiceConversationEntryRepository entryRepository;
    private final OpenAiStreamingService openAiStreamingService;
    private final InterviewContextService contextService;
    private final TextToSpeechService textToSpeechService;
    private final ToneAnalysisService toneAnalysisService;
    private final InterviewEvaluationService evaluationService;
    private final CandidatePerformanceAnalyzer performanceAnalyzer;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transactionTemplate;

    @Value("${interview.max-warnings:5}")
    private int maxWarnings;

    @Value("${interview.max-duration-minutes:60}")
    private int maxDurationMinutes;

    public VoiceInterviewServiceImpl(
            CandidateInterviewScheduleRepository scheduleRepo,
            VoiceConversationEntryRepository entryRepository,
            OpenAiStreamingService openAiStreamingService,
            InterviewContextService contextService,
            TextToSpeechService textToSpeechService,
            ToneAnalysisService toneAnalysisService,
            InterviewEvaluationService evaluationService,
            CandidatePerformanceAnalyzer performanceAnalyzer,
            SimpMessagingTemplate messagingTemplate,
            TransactionTemplate transactionTemplate) {
        this.scheduleRepo = scheduleRepo;
        this.entryRepository = entryRepository;
        this.openAiStreamingService = openAiStreamingService;
        this.contextService = contextService;
        this.textToSpeechService = textToSpeechService;
        this.toneAnalysisService = toneAnalysisService;
        this.evaluationService = evaluationService;
        this.performanceAnalyzer = performanceAnalyzer;
        this.messagingTemplate = messagingTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Strip [CODING], [THEORY], [NON-TECH] tags from text (used before TTS and DB save).
     */
    private String stripQuestionTypeTags(String text) {
        if (text == null) return null;
        return QUESTION_TYPE_TAG_PATTERN.matcher(text).replaceAll("").trim();
    }

//    @Override
//    @Transactional
//    public VoiceStartResponse startVoiceInterview(String jobPrefix, String email) {
//        CandidateInterviewSchedule schedule = scheduleRepo.findFirstByJobPrefixAndEmailOrderByAssignedAtDesc(jobPrefix, email)
//                .orElseThrow(() -> new RuntimeException("Interview schedule not found for " + jobPrefix + " / " + email));
//
//        // Item 4: If already IN_PROGRESS, return existing state instead of creating new
//        if (schedule.getAttemptStatus() == AttemptStatus.IN_PROGRESS) {
//            log.info("Resuming existing voice interview for schedule {} ({})", schedule.getId(), email);
//            List<VoiceConversationEntry> entries = entryRepository.findByInterviewScheduleIdOrderByTimestampAsc(schedule.getId());
//            String lastQuestion = entries.stream()
//                    .filter(e -> e.getRole() == ConversationRole.INTERVIEWER)
//                    .reduce((first, second) -> second)
//                    .map(VoiceConversationEntry::getContent)
//                    .orElse("Welcome back. Let's continue the interview.");
//
//            String lastQuestionAudio = textToSpeechService.generateTTSBase64(lastQuestion);
//
//            return VoiceStartResponse.builder()
//                    .scheduleId(schedule.getId())
//                    .firstQuestion(lastQuestion)
//                    .interviewerName(schedule.getInterviewerName())
//                    .firstQuestionAudio(lastQuestionAudio)
//                    .build();
//        }
//
//        schedule.setAttemptStatus(AttemptStatus.IN_PROGRESS);
//        schedule.setStartedAt(LocalDateTime.now());
//        schedule.setTotalQuestionsAsked(0);
//        schedule = scheduleRepo.save(schedule);
//
//        // Generate first question using DB system prompt
//        String firstQuestionPrompt = contextService.buildFirstQuestionPrompt();
//        List<Map<String, String>> messages = contextService.buildContextMessages(schedule, firstQuestionPrompt);
//        String firstQuestion = openAiStreamingService.chatCompletion(messages);
//
//        // Strip question type tags for TTS and DB (frontend detects tags from raw text)
//        String firstQuestionClean = stripQuestionTypeTags(firstQuestion);
//
//        // Save interviewer's first question (clean, without tags)
//        VoiceConversationEntry entry = VoiceConversationEntry.builder()
//                .interviewSchedule(schedule)
//                .role(ConversationRole.INTERVIEWER)
//                .content(firstQuestionClean)
//                .build();
//        entryRepository.save(entry);
//        schedule.incrementQuestionsAsked();
//        scheduleRepo.save(schedule);
//
//        // Generate TTS for first question (clean, without tags)
//        String firstQuestionAudio = textToSpeechService.generateTTSBase64(firstQuestionClean);
//
//        log.info("Started voice interview for schedule {} ({})", schedule.getId(), email);
//
//        return VoiceStartResponse.builder()
//                .scheduleId(schedule.getId())
//                .firstQuestion(firstQuestion)
//                .interviewerName(schedule.getInterviewerName())
//                .firstQuestionAudio(firstQuestionAudio)
//                .build();
//    }
    
    @Override
    @Transactional
    public VoiceStartResponse startVoiceInterview(String jobPrefix, String email) {
        CandidateInterviewSchedule schedule = scheduleRepo.findFirstByJobPrefixAndEmailOrderByAssignedAtDesc(jobPrefix, email)
                .orElseThrow(() -> new RuntimeException("Interview schedule not found for " + jobPrefix + " / " + email));

        // Resume existing interview if already IN_PROGRESS
        if (schedule.getAttemptStatus() == AttemptStatus.IN_PROGRESS) {
            log.info("Resuming existing voice interview for schedule {} ({})", schedule.getId(), email);
            List<VoiceConversationEntry> entries = entryRepository.findByInterviewScheduleIdOrderByTimestampAsc(schedule.getId());
            String lastQuestion = entries.stream()
                    .filter(e -> e.getRole() == ConversationRole.INTERVIEWER)
                    .reduce((first, second) -> second)
                    .map(VoiceConversationEntry::getContent)
                    .orElse("Welcome back. Let's continue the interview.");

            String lastQuestionAudio = textToSpeechService.generateTTSBase64(lastQuestion);

            return VoiceStartResponse.builder()
                    .scheduleId(schedule.getId())
                    .firstQuestion(lastQuestion)
                    .interviewerName(schedule.getInterviewerName())
                    .firstQuestionAudio(lastQuestionAudio)
                    .build();
        }

        // Start a new interview
        schedule.setAttemptStatus(AttemptStatus.IN_PROGRESS);
        schedule.setStartedAt(LocalDateTime.now());
        schedule.setTotalQuestionsAsked(0);
        schedule = scheduleRepo.save(schedule);

        // ==== Use the existing question loading logic ====
        String firstQuestionRaw = interviewService.prepareQuestionsAndCreateSession(jobPrefix, email, schedule.getId());
        // ================================================

        // Strip question type tags for TTS and DB storage (frontend still sees the raw tagged question)
        String firstQuestionClean = stripQuestionTypeTags(firstQuestionRaw);

        // Save interviewer's first question (clean) to the voice conversation table
        VoiceConversationEntry entry = VoiceConversationEntry.builder()
                .interviewSchedule(schedule)
                .role(ConversationRole.INTERVIEWER)
                .content(firstQuestionClean)
                .build();
        entryRepository.save(entry);

        schedule.incrementQuestionsAsked();
        scheduleRepo.save(schedule);

        // Generate TTS for the clean question
        String firstQuestionAudio = textToSpeechService.generateTTSBase64(firstQuestionClean);

        log.info("Started voice interview for schedule {} ({})", schedule.getId(), email);

        return VoiceStartResponse.builder()
                .scheduleId(schedule.getId())
                .firstQuestion(firstQuestionRaw)   // keep raw (with tags) for frontend detection
                .interviewerName(schedule.getInterviewerName())
                .firstQuestionAudio(firstQuestionAudio)
                .build();
    }

    @Override
    @Transactional
    public ResumeResponse resumeInterview(Long scheduleId) {
        return scheduleRepo.findByIdAndAttemptStatus(scheduleId, AttemptStatus.IN_PROGRESS)
                .map(schedule -> {
                    List<VoiceConversationEntry> entries = entryRepository
                            .findByInterviewScheduleIdOrderByTimestampAsc(scheduleId);

                    List<ResumeResponse.ConversationEntryDTO> history = entries.stream()
                            .map(e -> ResumeResponse.ConversationEntryDTO.builder()
                                    .role(e.getRole().name().toLowerCase())
                                    .content(e.getContent())
                                    .timestamp(e.getTimestamp().toString())
                                    .build())
                            .toList();

                    return ResumeResponse.builder()
                            .hasActiveSession(true)
                            .scheduleId(scheduleId)
                            .currentQuestionIndex(schedule.getTotalQuestionsAsked())
                            .warningCount(schedule.getWarningCount())
                            .conversationHistory(history)
                            .build();
                })
                .orElse(ResumeResponse.builder().hasActiveSession(false).build());
    }

//    @Override
//    public void processVoiceAnswer(Long scheduleId, VoiceAnswerRequest request) {
//        try {
//            boolean skipped = request.isSkipped();
//
//            // ── Phase 1: Short TX (~50ms) — Validate + Save candidate answer ──
//            CandidateInterviewSchedule schedule = transactionTemplate.execute(status -> {
//                CandidateInterviewSchedule s = scheduleRepo.findById(scheduleId)
//                        .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));
//
//                if (s.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
//                    throw new RuntimeException("Interview is not active");
//                }
//
//                if (skipped) {
//                    // Save skipped entry — no tone analysis needed
//                    VoiceConversationEntry candidateEntry = VoiceConversationEntry.builder()
//                            .interviewSchedule(s)
//                            .role(ConversationRole.CANDIDATE)
//                            .content("[NO RESPONSE - SKIPPED]")
//                            .build();
//                    entryRepository.save(candidateEntry);
//                } else {
//                    // Analyze tone metrics
//                    double speechDuration = (request.getWordTimestamps() != null && !request.getWordTimestamps().isEmpty())
//                            ? request.getWordTimestamps().get(request.getWordTimestamps().size() - 1).getEnd()
//                            : 0;
//                    ToneAnalysisService.ToneMetrics toneMetrics = toneAnalysisService.analyze(
//                            request.getTranscript(), request.getWordTimestamps(), speechDuration);
//
//                    // Build content — append code block if code was submitted
//                    String answerContent = request.getTranscript();
//                    if (request.getCodeContent() != null && !request.getCodeContent().isBlank()) {
//                        answerContent += "\n\n[CODE (" + (request.getCodeLanguage() != null ? request.getCodeLanguage() : "text") + ")]\n" + request.getCodeContent();
//                    }
//
//                    // Save candidate answer
//                    VoiceConversationEntry candidateEntry = VoiceConversationEntry.builder()
//                            .interviewSchedule(s)
//                            .role(ConversationRole.CANDIDATE)
//                            .content(answerContent)
//                            .wordCount(toneMetrics.getWordCount())
//                            .wordsPerMinute(toneMetrics.getWordsPerMinute())
//                            .fillerWordCount(toneMetrics.getFillerWordCount())
//                            .confidenceScore(toneMetrics.getConfidenceScore())
//                            .speechDurationSeconds(toneMetrics.getSpeechDurationSeconds())
//                            .codeContent(request.getCodeContent())
//                            .codeLanguage(request.getCodeLanguage())
//                            .build();
//                    entryRepository.save(candidateEntry);
//                }
//
//                return s;
//            });
//
//            // Send filler only for real answers (skips don't need "let me think...")
//            if (!skipped) {
//                String filler = FILLERS.get(random.nextInt(FILLERS.size()));
//                sendFiller(scheduleId, filler);
//            }
//
//            // ── Phase 2: No TX — OpenAI calls (3-30s) ──
//
//            // Update running summary (synchronous OpenAI call)
//            String summary = contextService.updateRunningSummary(schedule);
//            if (summary != null) {
//                // Short TX to persist the summary
//                transactionTemplate.execute(new TransactionCallbackWithoutResult() {
//                    @Override
//                    protected void doInTransactionWithoutResult(TransactionStatus status) {
//                        CandidateInterviewSchedule fresh = scheduleRepo.findById(scheduleId)
//                                .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));
//                        fresh.setRunningSummary(summary);
//                        scheduleRepo.save(fresh);
//                    }
//                });
//                // Update local reference for context building
//                schedule.setRunningSummary(summary);
//            }
//
//            // Compute performance snapshot before building prompt (guidance is injected by contextService)
//            PerformanceSnapshot performanceSnapshot = performanceAnalyzer.analyze(schedule);
//
//            // Build prompt — AI decides what to ask next (or end the interview)
//            String userMessage = contextService.buildNextQuestionPrompt(schedule, request.getTranscript(), skipped,
//                    request.getCodeContent(), request.getCodeLanguage());
//            List<Map<String, String>> messages = contextService.buildContextMessages(schedule, userMessage);
//
//            // Stream GPT response
//            openAiStreamingService.streamChatCompletion(messages,
//                    // onToken
//                    token -> {
//                        Map<String, Object> tokenMsg = new HashMap<>();
//                        tokenMsg.put("token", token);
//                        tokenMsg.put("done", false);
//                        messagingTemplate.convertAndSend(
//                                "/topic/interview/" + scheduleId + "/ai-token",
//                                tokenMsg
//                        );
//                    },
//                    // onComplete
//                    completeText -> {
//                        try {
//                            // Check if AI decided to end the interview
//                            boolean isComplete = completeText.contains(INTERVIEW_COMPLETE_MARKER);
//                            String cleanedText = completeText.replace(INTERVIEW_COMPLETE_MARKER, "").trim();
//
//                            // Send fullText WITH tags to frontend (frontend detects [CODING] etc.)
//                            Map<String, Object> doneMsg = new HashMap<>();
//                            doneMsg.put("token", "");
//                            doneMsg.put("done", true);
//                            doneMsg.put("fullText", cleanedText);
//                            messagingTemplate.convertAndSend(
//                                    "/topic/interview/" + scheduleId + "/ai-token",
//                                    doneMsg
//                            );
//
//                            // Strip question type tags for DB and TTS
//                            String textForStorage = stripQuestionTypeTags(cleanedText);
//
//                            // ── Phase 3: Short TX (~50ms) — Save interviewer response ──
//                            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
//                                @Override
//                                protected void doInTransactionWithoutResult(TransactionStatus status) {
//                                    CandidateInterviewSchedule fresh = scheduleRepo.findById(scheduleId)
//                                            .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));
//
//                                    VoiceConversationEntry entry = VoiceConversationEntry.builder()
//                                            .interviewSchedule(fresh)
//                                            .role(ConversationRole.INTERVIEWER)
//                                            .content(textForStorage)
//                                            .build();
//                                    entryRepository.save(entry);
//                                    fresh.incrementQuestionsAsked();
//
//                                    if (isComplete) {
//                                        fresh.setAttemptStatus(AttemptStatus.COMPLETED);
//                                        fresh.setEndedAt(LocalDateTime.now());
//                                        fresh.setCompletionReason(
//                                                performanceSnapshot.isEarlyTerminationSuggested()
//                                                        ? CompletionReason.EARLY_TERMINATION_POOR_PERFORMANCE
//                                                        : CompletionReason.NATURAL_COMPLETION);
//                                    }
//                                    scheduleRepo.save(fresh);
//                                }
//                            });
//
//                            // Send response-complete and TTS (no TX needed)
//                            CandidateInterviewSchedule updated = scheduleRepo.findById(scheduleId)
//                                    .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));
//                            sendResponseComplete(scheduleId, updated, isComplete);
//                            textToSpeechService.generateAndStreamTTS(scheduleId, textForStorage);
//
//                            if (isComplete) {
//                                evaluationService.triggerEvaluationAsync(scheduleId);
//                            }
//                        } catch (Exception e) {
//                            log.error("Error in onComplete callback for schedule {}: {}", scheduleId, e.getMessage(), e);
//                            sendResponseCompleteError(scheduleId);
//                        }
//                    }
//            );
//        } catch (Exception e) {
//            log.error("Error processing voice answer for schedule {}: {}", scheduleId, e.getMessage(), e);
//            sendResponseCompleteError(scheduleId);
//        }
//    }
	
    @Override
    public void processVoiceAnswer(Long scheduleId, VoiceAnswerRequest request) {
        boolean skipped = request.isSkipped();
        
        // --- Phase 1: Save candidate answer (same as before) ---
        CandidateInterviewSchedule schedule = transactionTemplate.execute(status -> {
            CandidateInterviewSchedule s = scheduleRepo.findById(scheduleId)
                    .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));
            if (s.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
                throw new RuntimeException("Interview is not active");
            }
            if (skipped) {
                VoiceConversationEntry candidateEntry = VoiceConversationEntry.builder()
                        .interviewSchedule(s)
                        .role(ConversationRole.CANDIDATE)
                        .content("[NO RESPONSE - SKIPPED]")
                        .build();
                entryRepository.save(candidateEntry);
            } else {
                double speechDuration = (request.getWordTimestamps() != null && !request.getWordTimestamps().isEmpty())
                        ? request.getWordTimestamps().get(request.getWordTimestamps().size() - 1).getEnd()
                        : 0;
                ToneAnalysisService.ToneMetrics toneMetrics = toneAnalysisService.analyze(
                        request.getTranscript(), request.getWordTimestamps(), speechDuration);
                String answerContent = request.getTranscript();
                if (request.getCodeContent() != null && !request.getCodeContent().isBlank()) {
                    answerContent += "\n\n[CODE (" + (request.getCodeLanguage() != null ? request.getCodeLanguage() : "text") + ")]\n" + request.getCodeContent();
                }
                VoiceConversationEntry candidateEntry = VoiceConversationEntry.builder()
                        .interviewSchedule(s)
                        .role(ConversationRole.CANDIDATE)
                        .content(answerContent)
                        .wordCount(toneMetrics.getWordCount())
                        .wordsPerMinute(toneMetrics.getWordsPerMinute())
                        .fillerWordCount(toneMetrics.getFillerWordCount())
                        .confidenceScore(toneMetrics.getConfidenceScore())
                        .speechDurationSeconds(toneMetrics.getSpeechDurationSeconds())
                        .codeContent(request.getCodeContent())
                        .codeLanguage(request.getCodeLanguage())
                        .build();
                entryRepository.save(candidateEntry);
            }
            return s;
        });

        if (!skipped) {
            String filler = FILLERS.get(random.nextInt(FILLERS.size()));
            sendFiller(scheduleId, filler);
        }

        // --- Phase 2: Use InterviewService.answer ---
     // --- Phase 2: Use InterviewService.answer ---
        try {
            String jobPrefix = schedule.getJobPrefix();
            String result = interviewService.answer(
                    scheduleId,
                    skipped ? "" : request.getTranscript(),
                    false,
                    jobPrefix,
                    request.getCodeContent(),
                    request.getCodeLanguage()
            );
            System.out.println("Sending response: " + result);

            // 1. Handle RETRY message (language warning) – do not complete the interview
            if (result.startsWith("RETRY:")) {
                sendResponseComplete(scheduleId, result, false);
                // No further actions – schedule remains IN_PROGRESS
                return;
            }

            // 2. Handle normal FEEDBACK + NEXT QUESTION
            if (result.startsWith("FEEDBACK:")) {
                // Parse feedback and next question
                String[] parts = result.split("NEXT QUESTION:", 2);
                if (parts.length == 2) {
                    String feedback = parts[0].replace("FEEDBACK:", "").trim();
                    String nextQuestion = parts[1].trim();

                    // Save interviewer's next question to DB
                    transactionTemplate.execute(status -> {
                        CandidateInterviewSchedule s = scheduleRepo.findById(scheduleId).orElseThrow();
                        VoiceConversationEntry entry = VoiceConversationEntry.builder()
                                .interviewSchedule(s)
                                .role(ConversationRole.INTERVIEWER)
                                .content(nextQuestion)
                                .build();
                        entryRepository.save(entry);
                        s.incrementQuestionsAsked();
                        scheduleRepo.save(s);
                        return null;
                    });

                    // Generate TTS for the next question
                    String cleanNextQuestion = stripQuestionTypeTags(nextQuestion);
                    textToSpeechService.generateAndStreamTTS(scheduleId, cleanNextQuestion);

                    // Send the response to frontend
                    sendResponseComplete(scheduleId, result, false);
                } else {
                    // Malformed FEEDBACK message – just send error
//                    sendResponseCompleteError(scheduleId);
                }
                return;
            }

            // 3. Final summary – interview is complete
            transactionTemplate.execute(status -> {
                CandidateInterviewSchedule s = scheduleRepo.findById(scheduleId).orElseThrow();
                s.setAttemptStatus(AttemptStatus.COMPLETED);
                s.setEndedAt(LocalDateTime.now());
                scheduleRepo.save(s);
                return null;
            });
            sendResponseComplete(scheduleId, result, true);
            textToSpeechService.generateAndStreamTTS(scheduleId, result);
            evaluationService.triggerEvaluationAsync(scheduleId);

        } catch (Exception e) {
            log.error("Error processing answer via InterviewService for schedule {}: {}", scheduleId, e.getMessage(), e);
//            sendResponseCompleteError(scheduleId);
        }
    }
    @Override
    @Transactional
    public void endVoiceInterview(Long scheduleId) {
        CandidateInterviewSchedule schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));

        schedule.setAttemptStatus(AttemptStatus.COMPLETED);
        schedule.setEndedAt(LocalDateTime.now());
        schedule.setCompletionReason(CompletionReason.CANDIDATE_ENDED);
        scheduleRepo.save(schedule);

        // Fire-and-forget async evaluation generation
        evaluationService.triggerEvaluationAsync(scheduleId);

        log.info("Voice interview {} ended", scheduleId);
    }

    @Override
    @Transactional
    public boolean handleWarning(Long scheduleId) {
        CandidateInterviewSchedule schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));

        schedule.addWarning();
        scheduleRepo.save(schedule);

        if (schedule.getWarningCount() >= maxWarnings) {
            schedule.setAttemptStatus(AttemptStatus.COMPLETED);
            schedule.setInterviewResult(InterviewResult.FAILED);
            schedule.setEndedAt(LocalDateTime.now());
            schedule.setCompletionReason(CompletionReason.PROCTORING_VIOLATION);
            scheduleRepo.save(schedule);
            log.warn("Schedule {} terminated due to excessive warnings", scheduleId);
            return true;
        }

        return false;
    }

    @Override
    public VoiceSessionStatus getSessionStatus(Long scheduleId) {
        CandidateInterviewSchedule schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));

        return VoiceSessionStatus.builder()
                .scheduleId(schedule.getId())
                .status(schedule.getAttemptStatus().name())
                .totalQuestionsAsked(schedule.getTotalQuestionsAsked())
                .warningCount(schedule.getWarningCount())
                .startedAt(schedule.getStartedAt())
                .interviewerName(schedule.getInterviewerName())
                .build();
    }

    @Override
    public VoiceEvaluationResult getEvaluation(Long scheduleId) {
        return evaluationService.evaluateInterview(scheduleId);
    }

    /**
     * Item 7: Backend interview timeout enforcement.
     * Runs every 5 minutes to find and auto-complete stale IN_PROGRESS interviews.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void enforceInterviewTimeouts() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(maxDurationMinutes);
        List<CandidateInterviewSchedule> staleInterviews =
                scheduleRepo.findByAttemptStatusAndStartedAtBefore(AttemptStatus.IN_PROGRESS, cutoff);

        for (CandidateInterviewSchedule schedule : staleInterviews) {
            log.warn("Auto-completing stale interview {} (started at {})", schedule.getId(), schedule.getStartedAt());
            schedule.setAttemptStatus(AttemptStatus.COMPLETED);
            schedule.setEndedAt(LocalDateTime.now());
            schedule.setCompletionReason(CompletionReason.TIMEOUT);
            scheduleRepo.save(schedule);

            // Try to generate evaluation
            try {
                evaluationService.triggerEvaluationAsync(schedule.getId());
            } catch (Exception e) {
                log.error("Failed to trigger evaluation for timed-out interview {}", schedule.getId(), e);
            }

            // Notify client if still connected
            messagingTemplate.convertAndSend(
                    "/topic/interview/" + schedule.getId() + "/response-complete",
                    Map.of("isComplete", true, "terminated", true, "reason", "Interview timed out")
            );
        }
    }

    private void saveInterviewerResponse(CandidateInterviewSchedule schedule, String response) {
        VoiceConversationEntry entry = VoiceConversationEntry.builder()
                .interviewSchedule(schedule)
                .role(ConversationRole.INTERVIEWER)
                .content(response)
                .build();
        entryRepository.save(entry);
        schedule.incrementQuestionsAsked();
        scheduleRepo.save(schedule);
    }

    private void sendFiller(Long scheduleId, String filler) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("text", filler);
        msg.put("type", "filler");
        messagingTemplate.convertAndSend("/topic/interview/" + scheduleId + "/filler", msg);
    }
//
//    private void sendResponseComplete(Long scheduleId, CandidateInterviewSchedule schedule, boolean isComplete) {
//        sendResponseComplete(scheduleId, schedule, isComplete, false);
//    }
//
//    private void sendResponseComplete(Long scheduleId, CandidateInterviewSchedule schedule, boolean isComplete, boolean terminated) {
//        Map<String, Object> msg = new HashMap<>();
//        msg.put("questionsAsked", schedule.getTotalQuestionsAsked());
//        msg.put("isComplete", isComplete);
//        msg.put("terminated", terminated);
//        messagingTemplate.convertAndSend("/topic/interview/" + scheduleId + "/response-complete", msg);
//    }
//
//    private void sendResponseCompleteError(Long scheduleId) {
//        Map<String, Object> msg = new HashMap<>();
//        msg.put("questionsAsked", -1);
//        msg.put("isComplete", false);
//        msg.put("error", true);
//        messagingTemplate.convertAndSend("/topic/interview/" + scheduleId + "/response-complete", msg);
//    }
    
 // Send response using a String (fetches schedule from DB)
    private void sendResponseComplete(Long scheduleId, String response, boolean isComplete) {
        CandidateInterviewSchedule schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found: " + scheduleId));
        sendResponseComplete(scheduleId, schedule, response, isComplete);
    }

    // Send response using an existing schedule object (avoids extra DB query)
    private void sendResponseComplete(Long scheduleId, CandidateInterviewSchedule schedule, String response, boolean isComplete) {
        Map<String, Object> message = new HashMap<>();
        message.put("response", response);
        message.put("questionsAsked", schedule.getTotalQuestionsAsked());
        message.put("isComplete", isComplete);
        message.put("terminated", false);
        messagingTemplate.convertAndSend("/topic/interview/" + scheduleId + "/response-complete", message);
    }

    // Keep the original method if it exists – you may adjust it to call the new one
    private void sendResponseComplete(Long scheduleId, CandidateInterviewSchedule schedule, boolean isComplete) {
        sendResponseComplete(scheduleId, schedule, null, isComplete);  // or build a default message
    }
}
