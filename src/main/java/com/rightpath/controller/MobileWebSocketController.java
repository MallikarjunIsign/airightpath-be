package com.rightpath.controller;

import com.rightpath.service.MobileConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/api")
public class MobileWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MobileConnectionService mobileConnectionService;

    // Desktop registers with token
    @MessageMapping("/desktop/register")
    public void registerDesktop(@Payload Map<String, String> payload,
                                org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        String token = payload.get("token");
        String sessionId = headerAccessor.getSessionId();
        mobileConnectionService.registerDesktop(token, sessionId);

        // Notify mobile that desktop is ready (to trigger offer)
        String mobileSession = mobileConnectionService.getMobileSession(token);
        if (mobileSession != null) {
            messagingTemplate.convertAndSendToUser(mobileSession, "/queue/mobile/ready", Map.of("status", "ready"));
        }
    }

    // Mobile registers with token
    @MessageMapping("/mobile/register")
    public void registerMobile(@Payload Map<String, String> payload,
                               org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        String token = payload.get("token");
        String sessionId = headerAccessor.getSessionId();
        mobileConnectionService.registerMobile(token, sessionId);

        // Notify desktop that mobile is ready
        String desktopSession = mobileConnectionService.getDesktopSession(token);
        if (desktopSession != null) {
            messagingTemplate.convertAndSendToUser(desktopSession, "/queue/mobile/ready", Map.of("status", "ready"));
        }
    }

    // Offer from desktop to mobile
    @MessageMapping("/mobile/offer/{token}")
    public void handleOffer(@DestinationVariable String token, @Payload Map<String, Object> offer) {
        String mobileSession = mobileConnectionService.getMobileSession(token);
        if (mobileSession != null) {
            messagingTemplate.convertAndSendToUser(mobileSession, "/queue/mobile/offer", offer);
        }
    }

    // Answer from mobile to desktop
    @MessageMapping("/mobile/answer/{token}")
    public void handleAnswer(@DestinationVariable String token, @Payload Map<String, Object> answer) {
        String desktopSession = mobileConnectionService.getDesktopSession(token);
        if (desktopSession != null) {
            messagingTemplate.convertAndSendToUser(desktopSession, "/queue/mobile/answer", answer);
        }
    }

    // Verification from mobile to desktop
    @MessageMapping("/mobile/verified/{token}")
    public void handleVerified(@DestinationVariable String token, @Payload Map<String, Object> payload) {
        String desktopSession = mobileConnectionService.getDesktopSession(token);
        if (desktopSession != null) {
            messagingTemplate.convertAndSendToUser(desktopSession, "/queue/mobile/verified", payload);
        }
    }

    // ICE candidate exchange
    @MessageMapping("/mobile/ice/{token}")
    public void handleIceCandidate(@DestinationVariable String token, @Payload Map<String, Object> candidate) {
        String target = (String) candidate.get("target");
        String sessionId = "mobile".equals(target)
                ? mobileConnectionService.getMobileSession(token)
                : mobileConnectionService.getDesktopSession(token);
        if (sessionId != null) {
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/mobile/ice", candidate);
        }
    }
}