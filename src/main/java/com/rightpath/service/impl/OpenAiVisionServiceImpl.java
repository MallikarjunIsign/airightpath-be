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
		Analyze this image carefully.
		
		Check the following:
		
		1. Is exactly ONE person visible?
		2. Is the person sitting in front of a computer or laptop?
		3. Are other people visible in the room?
		
		Return ONLY JSON:
		
		{
		 "status": "VERIFIED or FAILED",
		 "reason": ""
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
		 "max_tokens":200
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