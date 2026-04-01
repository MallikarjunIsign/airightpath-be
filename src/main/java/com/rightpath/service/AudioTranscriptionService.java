package com.rightpath.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.rightpath.util.TranscriptionResult;

@Service
public class AudioTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);

    private final OpenAiStreamingService openAiStreamingService;
    private final SimpMessagingTemplate messagingTemplate;

    public AudioTranscriptionService(OpenAiStreamingService openAiStreamingService,
                                     SimpMessagingTemplate messagingTemplate) {
        this.openAiStreamingService = openAiStreamingService;
        this.messagingTemplate = messagingTemplate;
    }

    @Async("transcriptionExecutor")
    public void transcribeAsync(Long scheduleId, byte[] audioData, int chunkIndex) {
        log.info("Transcribing chunk {} for schedule {} ({} bytes)", chunkIndex, scheduleId, audioData.length);

        try {
            String filename = String.format("chunk_%d.webm", chunkIndex);
            TranscriptionResult result = openAiStreamingService.transcribe(audioData, filename);

            if (result.getText() != null && !result.getText().isBlank()) {
                Map<String, Object> message = new HashMap<>();
                message.put("text", result.getText());
                message.put("words", result.getWords());
                message.put("duration", result.getDuration());
                message.put("chunkIndex", chunkIndex);

                messagingTemplate.convertAndSend(
                        "/topic/interview/" + scheduleId + "/transcription",
                        message
                );

                log.info("Transcribed chunk {} for schedule {}: \"{}\"", chunkIndex, scheduleId, result.getText());
            } else {
                log.warn("Chunk {} for schedule {} produced empty transcription (audio may be silence or corrupt)",
                        chunkIndex, scheduleId);
            }
        } catch (Exception e) {
            log.error("Error transcribing audio chunk {} for schedule {}", chunkIndex, scheduleId, e);

            Map<String, Object> errorMsg = new HashMap<>();
            errorMsg.put("error", "Transcription failed for chunk " + chunkIndex);
            errorMsg.put("chunkIndex", chunkIndex);

            messagingTemplate.convertAndSend(
                    "/topic/interview/" + scheduleId + "/transcription-error",
                    errorMsg
            );
        }
    }
}
