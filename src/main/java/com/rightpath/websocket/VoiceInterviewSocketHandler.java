//package com.rightpath.websocket;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.rightpath.entity.InterviewSession;
//import com.rightpath.service.InterviewSessionManager;
//import com.rightpath.service.MainInterviewAiService;
//import com.rightpath.service.SpeechToTextService;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.*;
//import org.springframework.web.socket.handler.AbstractWebSocketHandler;
//import org.springframework.web.util.UriComponentsBuilder;
//
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Component
//public class VoiceInterviewSocketHandler extends AbstractWebSocketHandler {
//
////    private final InterviewSessionManager sessionManager;
////    private final SpeechToTextService speechAi;
////    private final MainInterviewAiService mainAi;
////
////    private final Map<String, StringBuilder> transcripts = new ConcurrentHashMap<>();
////    private final ObjectMapper mapper = new ObjectMapper();
////
////    public VoiceInterviewSocketHandler(
////            InterviewSessionManager sessionManager,
////            SpeechToTextService speechAi,
////            MainInterviewAiService mainAi) {
////        this.sessionManager = sessionManager;
////        this.speechAi = speechAi;
////        this.mainAi = mainAi;
////    }
////
////    @Override
////    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
////
////        var params = UriComponentsBuilder.fromUri(ws.getUri())
////                .build()
////                .getQueryParams();
////
////        String jobPrefix = params.getFirst("jobPrefix");
////        String email = params.getFirst("email");
////
////        if (jobPrefix == null || email == null) {
////            ws.close(CloseStatus.BAD_DATA);
////            return;
////        }
////
////        // ✅ DO NOT close socket — create session if missing
////        sessionManager.createIfAbsent(jobPrefix, email);
////
////        transcripts.put(ws.getId(), new StringBuilder());
////
////        // ✅ Control message — frontend must IGNORE
////        ws.sendMessage(new TextMessage("VOICE_SOCKET_CONNECTED"));
////    }
////
////    // 🎙️ AUDIO → TRANSCRIBE ONLY
////    @Override
////    protected void handleBinaryMessage(WebSocketSession ws, BinaryMessage message) {
////
////        String text = speechAi.convert(message.getPayload().array());
////
////        transcripts.computeIfAbsent(ws.getId(), k -> new StringBuilder())
////                   .append(text)
////                   .append(" ");
////    }
////
////    // 🎯 CONTROL
////    @Override
////    protected void handleTextMessage(WebSocketSession ws, TextMessage message)
////            throws Exception {
////
////        String payload = message.getPayload();
////
////        if (!payload.contains("END_ANSWER")) return;
////
////        var params = UriComponentsBuilder.fromUri(ws.getUri())
////                .build()
////                .getQueryParams();
////
////        String jobPrefix = params.getFirst("jobPrefix");
////        String email = params.getFirst("email");
////
////        InterviewSession session =
////                sessionManager.get(jobPrefix, email);
////
////        if (session == null) return;
////
////        String answer = transcripts.get(ws.getId()).toString().trim();
////        transcripts.get(ws.getId()).setLength(0);
////
////        if (answer.isEmpty()) {
////            answer = "No answer provided.";
////        }
////
////        String lastQuestion =
////                session.getMessages().stream()
////                        .filter(m -> "assistant".equals(m.get("role")))
////                        .reduce((a, b) -> b)
////                        .map(m -> m.get("content"))
////                        .orElse("");
////
////        String nextQuestion =
////                mainAi.processAnswer(session, lastQuestion, answer);
////
////        if (nextQuestion == null || nextQuestion.isBlank()) {
////            nextQuestion = "Interview completed.";
////        }
////
////        Map<String, Object> response = Map.of(
////                "nextQuestion", nextQuestion,
////                "answer", answer,
////                "result", "PROCESSED",
////                "completed", false
////        );
////
////        ws.sendMessage(new TextMessage(mapper.writeValueAsString(response)));
////    }
////
////    @Override
////    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
////        transcripts.remove(ws.getId());
////    }
//}
