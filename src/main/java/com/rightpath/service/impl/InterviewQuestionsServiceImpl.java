package com.rightpath.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightpath.dto.InterviewQuestionInfo;
import com.rightpath.entity.UploadInterviewQuestions;
import com.rightpath.repository.InterviewQuestionsRepository;
import com.rightpath.service.InterviewQuestionsService;
import com.rightpath.service.StorageService;


@Service
public class InterviewQuestionsServiceImpl implements InterviewQuestionsService {

    @Autowired
    private StorageService storageService; //  your existing S3 service

    @Autowired
    private InterviewQuestionsRepository repository;

    private static final String PREFIX = "interview";

    public void uploadInterviewQuestions(String jobPrefix, MultipartFile file) {

        try {
            //  Step 1: Get original file name (NO generation)
            String fileName = file.getOriginalFilename();

            if (fileName == null || fileName.isBlank()) {
                throw new RuntimeException("File name is missing");
            }

            //  Step 2: Upload using existing S3 service
            storageService.uploadFile(PREFIX, fileName, file);

            //  Step 3: Save SAME key in DB
            String key = PREFIX + "/" + fileName;

            UploadInterviewQuestions entity = new UploadInterviewQuestions();
            entity.setJobPrefix(jobPrefix);
            entity.setFileName(key);

            repository.save(entity);

            System.out.println("✅ Interview file uploaded & saved: " + key);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("❌ Failed to upload interview questions");
        }
    }
    
    @Override
    public String fetchInterviewQuestions(String jobPrefix) {

        try {
            // ✅ Step 1: Get latest file from DB
            UploadInterviewQuestions entity = repository
                    .findByJobPrefixOrderByIdDesc(jobPrefix)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No file found for jobPrefix"));

            //  Step 2: Extract key
            String key = entity.getFileName(); // interview/file.json

            //  Step 3: Split prefix + fileName
            String[] parts = key.split("/", 2);

            if (parts.length < 2) {
                throw new RuntimeException("Invalid S3 key format");
            }

            String prefix = parts[0];   // interview
            String fileName = parts[1]; // actual file

            //  Step 4: Download from S3
            String content = storageService.downloadFileAsText(prefix, fileName);

            return content;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("❌ Failed to fetch interview questions");
        }
    }
    
    
    public List<InterviewQuestionInfo> loadAndPrepareQuestions(String jobPrefix) {
        String content = fetchInterviewQuestions(jobPrefix);
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode root = mapper.readTree(content);
            JsonNode questionsNode = root.get("questions");
            List<InterviewQuestionInfo> allQuestions = new ArrayList<>();
            System.out.println("Loaded " + allQuestions.size() + " raw questions from S3");
            for (JsonNode node : questionsNode) {
                String uniqueId = node.get("uniqueId").asText();
                String level = node.get("level").asText();
                String questionText = node.get("question").asText();
                allQuestions.add(new InterviewQuestionInfo(questionText, uniqueId, level));
            }
            return prepareInterviewQuestions(allQuestions);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load questions", e);
        }
    }

    private List<InterviewQuestionInfo> prepareInterviewQuestions(List<InterviewQuestionInfo> allQuestions) {
        List<InterviewQuestionInfo> technical = new ArrayList<>();
        List<InterviewQuestionInfo> coding = new ArrayList<>();
        List<InterviewQuestionInfo> snippet = new ArrayList<>();

        for (InterviewQuestionInfo q : allQuestions) {
            String text = q.getText();
            if (text.startsWith("[CODING]")) {
                coding.add(q);
            } else if (text.contains("code snippet")
                    || text.contains("output of the following code")
                    || text.contains("what happens in this code")) {
                snippet.add(q);
            } else {
                technical.add(q);
            }
        }

        Collections.shuffle(technical);
        Collections.shuffle(coding);
        Collections.shuffle(snippet);

        List<InterviewQuestionInfo> selected = new ArrayList<>();
        System.out.println("Final selected questions: " + selected.size());
        selected.addAll(technical.subList(0, Math.min(7, technical.size())));
        selected.addAll(coding.subList(0, Math.min(2, coding.size())));
        selected.addAll(snippet.subList(0, Math.min(2, snippet.size())));

        Collections.shuffle(selected);

        // Add intro question as a synthetic entry
        InterviewQuestionInfo intro = new InterviewQuestionInfo("Tell me about yourself", "INTRO", "INTRODUCTION");
        selected.add(0, intro);

        return selected;
    }
}