//package com.rightpath.service.impl;
//
//import java.util.List;
//
//import java.util.Map;
//
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//
//import com.rightpath.dto.ChatCompletionRequest;
//import com.rightpath.dto.ChatCompletionResponse;
//import com.rightpath.entity.InterviewSession;
//import com.rightpath.service.MainInterviewAiService;
//
//@Service
//public class MainInterviewAiServiceImpl implements MainInterviewAiService {
//
//	private final WebClient openAiWebClient;
//
//    public MainInterviewAiServiceImpl(
//            @Qualifier("openAiWebClient") WebClient openAiWebClient) {
//        this.openAiWebClient = openAiWebClient;
//    }
//		
//			    private static final String SYSTEM_PROMPT = """
//			You are an expert technical interviewer.
//		
//			Rules:
//			- Ask ONE question at a time.
//			- Use previous questions and answers as context.
//			- Label questions as [THEORY], [CODING], or [NON-TECH].
//			- Ask follow-ups only for THEORY / NON-TECH.
//			- Do NOT ask follow-ups for CODING.
//			- When interview is complete, say exactly:
//			  Interview completed.
//			""";
//		
//			    // ---------- START ----------
//			    public String startInterview(InterviewSession session, String resumeSummary) {
//		
//			        session.getMessages().clear();
//		
//			        session.getMessages().add(Map.of(
//			                "role", "system",
//			                "content", SYSTEM_PROMPT
//			        ));
//		
//			        session.getMessages().add(Map.of(
//			                "role", "user",
//			                "content", "Start interview. Resume summary:\n" + resumeSummary
//			        ));
//		
//			        return askAi(session);
//			    }
//		
//			    // ---------- PROCESS ANSWER ----------
//			    public String processAnswer(
//			            InterviewSession session,
//			            String lastQuestion,
//			            String answerText) {
//		
//			        session.getMessages().add(Map.of("role", "assistant", "content", lastQuestion));
//			        session.getMessages().add(Map.of("role", "user", "content", answerText));
//		
//			        return askAi(session);
//			    }
//		
//			    // ---------- SUMMARY ----------
//			    public String generateSummary(InterviewSession session) {
//		
//			        ChatCompletionRequest req = new ChatCompletionRequest();
//			        req.setModel("gpt-5");
//			        req.setMessages(List.of(
//			                Map.of("role", "system",
//			                        "content", "You are an expert HR interview summarizer."),
//			                Map.of("role", "user",
//			                        "content",
//			                        """
//			Summarize this interview into:
//			- Candidate strengths
//			- Weak points
//			- Technical knowledge
//			- Communication
//			- Final evaluation
//		
//			Interview transcript:
//			""" + session.getMessages())
//			        ));
//		
//			        ChatCompletionResponse res = openAiWebClient.post()
//			                .uri("/chat/completions")
//			                .bodyValue(req)
//			                .retrieve()
//			                .bodyToMono(ChatCompletionResponse.class)
//			                .block();
//		
//			        return res.getChoices().get(0).getMessage().getContent();
//			    }
//		
//			    private String askAi(InterviewSession session) {
//		
//			        ChatCompletionRequest req = new ChatCompletionRequest();
//			        req.setModel("gpt-5");
//			        req.setMessages(session.getMessages());
//		
//			        ChatCompletionResponse res = openAiWebClient.post()
//			                .uri("/chat/completions")
//			                .bodyValue(req)
//			                .retrieve()
//			                .bodyToMono(ChatCompletionResponse.class)
//			                .block();
//		
//			        return res.getChoices().get(0).getMessage().getContent();
//			    }
//
//}
