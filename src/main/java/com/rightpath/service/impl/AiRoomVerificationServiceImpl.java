package com.rightpath.service.impl;

import com.rightpath.service.AiRoomVerificationService;
import com.rightpath.service.OpenAiVisionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiRoomVerificationServiceImpl implements AiRoomVerificationService {

    @Autowired
    private OpenAiVisionService openAiVisionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean verify(byte[] imageBytes) {
        try {
            String result = openAiVisionService.analyzeRoom(imageBytes);
            JsonNode node = objectMapper.readTree(result);
            String status = node.get("status").asText();
            return "VERIFIED".equalsIgnoreCase(status);
        } catch (Exception e) {
            // If AI verification fails, log it but you might want to return false to be safe
            return false;
        }
    }
}