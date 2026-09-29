package com.rightpath.controller;

import com.rightpath.service.AiRoomVerificationService;
import com.rightpath.service.MobileConnectionService;
import com.rightpath.service.OpenAiVisionService;
import com.rightpath.service.VoiceInterviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile") 
public class MobileVerificationController {

    @Autowired
    private AiRoomVerificationService verificationService;

    @Autowired
    private OpenAiVisionService openAiVisionService;

    @Autowired
    private VoiceInterviewService voiceInterviewService;

    @Autowired
    private MobileConnectionService mobileConnectionService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Checks the room shot the phone just took.
     *
     * <p>Returns why it failed as well as whether it did. The phone showed
     * "Verification failed. Please reposition the phone." for every outcome —
     * a second person in frame, a laptop screen out of shot, a blank photo and
     * an OpenAI outage all looked identical, and only some of those are
     * something the candidate can act on.</p>
     */
    @PostMapping("/verify-room")
    public ResponseEntity<Map<String, Object>> verifyRoom(@RequestParam String token,
                                                          @RequestParam("photo") MultipartFile photo) throws IOException {
        AiRoomVerificationService.RoomVerificationResult result =
                verificationService.verify(photo.getBytes());

        return ResponseEntity.ok(Map.of(
                "valid", result.valid(),
                "reason", result.reason(),
                // False means "we could not check", not "you failed" — the phone
                // offers a retry rather than telling them to move.
                "checkable", result.checkable()));
    }

    @PostMapping("/monitor")
    public ResponseEntity<Map<String, Object>> monitor(@RequestParam String token,
                                                      @RequestParam("photo") MultipartFile photo) throws IOException {
        String result = openAiVisionService.analyzeRoom(photo.getBytes());
        JsonNode node = objectMapper.readTree(result);
        String status = node.get("status").asText();
        String reason = node.get("reason").asText();

        if ("FAILED".equalsIgnoreCase(status)) {
            // Find desktop session for this token
            String desktopSession = mobileConnectionService.getDesktopSession(token);
            if (desktopSession != null) {
                // Notify desktop about the malpractice
                messagingTemplate.convertAndSendToUser(desktopSession, "/queue/mobile/warning",
                    Map.of("type", "malpractice", "reason", reason));
            }
        }

        return ResponseEntity.ok(Map.of("status", status, "reason", reason));
    }
}