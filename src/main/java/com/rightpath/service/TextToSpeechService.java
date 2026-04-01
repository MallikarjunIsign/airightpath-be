package com.rightpath.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TextToSpeechService {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeechService.class);

    private final OpenAiStreamingService openAiStreamingService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^.!?]+[.!?]+\\s*|[^.!?]+$");
    private static final int BATCH_SIZE = 3;

    public TextToSpeechService(OpenAiStreamingService openAiStreamingService,
                               SimpMessagingTemplate messagingTemplate) {
        this.openAiStreamingService = openAiStreamingService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Generate TTS for text and send audio chunks progressively via WebSocket.
     * Item 6: Batches 2-3 sentences per API call to reduce total calls by ~60%.
     */
    @Async("ttsExecutor")
    public void generateAndStreamTTS(Long scheduleId, String text) {
        List<String> sentences = splitIntoSentences(text);
        List<String> batches = batchSentences(sentences, BATCH_SIZE);

        for (int i = 0; i < batches.size(); i++) {
            String batch = batches.get(i).trim();
            if (batch.isEmpty()) continue;

            try {
                byte[] audioBytes = openAiStreamingService.textToSpeech(batch);

                if (audioBytes.length > 0) {
                    String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

                    Map<String, Object> message = new HashMap<>();
                    message.put("audio", base64Audio);
                    message.put("chunkIndex", i);
                    message.put("isLast", i == batches.size() - 1);
                    message.put("text", batch);

                    messagingTemplate.convertAndSend(
                            "/topic/interview/" + scheduleId + "/tts-audio",
                            message
                    );

                    log.debug("Sent TTS batch {}/{} for schedule {}", i + 1, batches.size(), scheduleId);
                } else {
                    sendTTSFallback(scheduleId, batch);
                }
            } catch (Exception e) {
                log.error("Error generating TTS for batch {} in schedule {}", i, scheduleId, e);
                sendTTSFallback(scheduleId, batch);
            }
        }
    }

    /**
     * Generate TTS for the complete text (non-chunked, for first question).
     * Returns base64 encoded mp3.
     */
    public String generateTTSBase64(String text) {
        try {
            byte[] audioBytes = openAiStreamingService.textToSpeech(text);
            if (audioBytes.length > 0) {
                return Base64.getEncoder().encodeToString(audioBytes);
            }
        } catch (Exception e) {
            log.error("Error generating TTS base64", e);
        }
        return null;
    }

    private void sendTTSFallback(Long scheduleId, String text) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("text", text);

        messagingTemplate.convertAndSend(
                "/topic/interview/" + scheduleId + "/tts-fallback",
                fallback
        );
    }

    /**
     * Group sentences into batches of up to batchSize for fewer TTS API calls.
     */
    private List<String> batchSentences(List<String> sentences, int batchSize) {
        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;

        for (String sentence : sentences) {
            if (count >= batchSize && current.length() > 0) {
                batches.add(current.toString().trim());
                current = new StringBuilder();
                count = 0;
            }
            current.append(sentence).append(" ");
            count++;
        }

        if (current.length() > 0) {
            batches.add(current.toString().trim());
        }

        return batches;
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        if (sentences.isEmpty() && !text.isBlank()) {
            sentences.add(text);
        }
        return sentences;
    }
}
