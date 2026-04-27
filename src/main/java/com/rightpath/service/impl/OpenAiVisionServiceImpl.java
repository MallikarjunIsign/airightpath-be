package com.rightpath.service.impl;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightpath.service.OpenAiVisionService;

@Service
public class OpenAiVisionServiceImpl implements OpenAiVisionService {
		
		    @Value("${openai.api.key}")
		    private String apiKey;
		
		    private final RestTemplate restTemplate = new RestTemplate();
		
		    @Override
		    public String analyzeRoom(byte[] imageBytes) {
		
		        try {
		
		            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
		
		            String prompt = """
		Analyze this proctoring image from a mobile device carefully.
		
		Verify the following:
		1. Is exactly ONE person visible? (The candidate)
		2. Is a computer/laptop screen visible in the frame?
		3. Are there any other people in the room?
		4. Is the candidate using another mobile phone, headset, or books?
		5. Is there anything else suspicious that might indicate cheating?
		
		The goal is to ensure only the candidate and their system are present.
		
		Return ONLY JSON:
		{
		 "status": "VERIFIED or FAILED",
		 "reason": "Short explanation if FAILED, otherwise empty string"
		}
		""";
		
		            String body = """
		{
		 "model":"gpt-4o",
		 "messages":[
		   {
		     "role":"user",
		     "content":[
		       {"type":"text","text":"%s"},
		       {"type":"image_url","image_url":{"url":"data:image/jpeg;base64,%s"}}
		     ]
		   }
		 ],
		 "response_format": { "type": "json_object" },
		 "max_tokens":300
		}
		""".formatted(prompt, base64Image);
		
		            HttpHeaders headers = new HttpHeaders();
		            headers.setContentType(MediaType.APPLICATION_JSON);
		            headers.setBearerAuth(apiKey);
		
		            HttpEntity<String> request = new HttpEntity<>(body, headers);
		
		            ResponseEntity<String> response =
		                    restTemplate.postForEntity(
		                            "https://api.openai.com/v1/chat/completions",
		                            request,
		                            String.class
		                    );
		
		            ObjectMapper mapper = new ObjectMapper();
		
		            JsonNode node = mapper.readTree(response.getBody());
		
		            return node
		                    .get("choices")
		                    .get(0)
		                    .get("message")
		                    .get("content")
		                    .asText();
		
		        } catch (Exception e) {
		            throw new RuntimeException("AI verification failed", e);
		        }
		    }
		}