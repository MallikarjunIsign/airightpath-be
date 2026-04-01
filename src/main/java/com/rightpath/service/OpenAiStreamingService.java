package com.rightpath.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightpath.util.TranscriptionResult;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

@Service
public class OpenAiStreamingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiStreamingService.class);

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String whisperModel;
    private final String ttsModel;
    private final String ttsVoice;

    public OpenAiStreamingService(
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.api.base-url}") String baseUrl,
            @Value("${openai.model}") String model,
            @Value("${openai.audio.model}") String whisperModel,
            @Value("${openai.tts.model:tts-1-hd}") String ttsModel,
            @Value("${openai.tts.voice:nova}") String ttsVoice) {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.whisperModel = whisperModel;
        this.ttsModel = ttsModel;
        this.ttsVoice = ttsVoice;
    }

    /**
     * Stream GPT response token by token.
     */
    public void streamChatCompletion(List<Map<String, String>> messages, Consumer<String> onToken, Consumer<String> onComplete) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("stream", true);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 500);

            String json = objectMapper.writeValueAsString(requestBody);
            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            StringBuilder fullText = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);

            EventSource.Factory factory = EventSources.createFactory(httpClient);
            factory.newEventSource(request, new EventSourceListener() {
                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    if ("[DONE]".equals(data)) {
                        onComplete.accept(fullText.toString());
                        latch.countDown();
                        return;
                    }
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                        if (!delta.isMissingNode() && !delta.isNull()) {
                            String token = delta.asText();
                            fullText.append(token);
                            onToken.accept(token);
                        }
                    } catch (Exception e) {
                        log.error("Error parsing SSE token", e);
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    log.error("SSE stream failed: {}", t != null ? t.getMessage() : "unknown error");
                    onComplete.accept(fullText.toString());
                    latch.countDown();
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    latch.countDown();
                }
            });

            latch.await(120, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Error streaming chat completion", e);
            onComplete.accept("");
        }
    }

    /**
     * Non-streaming chat completion for evaluations and summaries.
     */
    public String chatCompletion(List<Map<String, String>> messages) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 2000);

            String json = objectMapper.writeValueAsString(requestBody);
            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    log.error("Chat completion failed: {} {} — {}", response.code(), response.message(), responseBody);
                    return "";
                }
                JsonNode node = objectMapper.readTree(responseBody);
                return node.path("choices").path(0).path("message").path("content").asText();
            }
        } catch (Exception e) {
            log.error("Error in chat completion", e);
            return "";
        }
    }

    /**
     * Transcribe audio using Whisper API with word-level timestamps.
     */
    public TranscriptionResult transcribe(byte[] audioData, String filename) {
        try {
            RequestBody fileBody = RequestBody.create(audioData, MediaType.parse("audio/webm"));

            MultipartBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filename, fileBody)
                    .addFormDataPart("model", whisperModel)
                    .addFormDataPart("response_format", "verbose_json")
                    .addFormDataPart("timestamp_granularities[]", "word")
                    .build();

            Request request = new Request.Builder()
                    .url(baseUrl + "/audio/transcriptions")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("Whisper transcription failed: {} {} — {}", response.code(), response.message(), errorBody);
                    return new TranscriptionResult("", 0, Collections.emptyList());
                }

                String responseBody = response.body().string();
                JsonNode node = objectMapper.readTree(responseBody);

                String text = node.path("text").asText("");
                double duration = node.path("duration").asDouble(0);

                List<TranscriptionResult.Word> words = new ArrayList<>();
                JsonNode wordsNode = node.path("words");
                if (wordsNode.isArray()) {
                    for (JsonNode wordNode : wordsNode) {
                        words.add(new TranscriptionResult.Word(
                                wordNode.path("word").asText(),
                                wordNode.path("start").asDouble(),
                                wordNode.path("end").asDouble()
                        ));
                    }
                }

                return new TranscriptionResult(text, duration, words);
            }
        } catch (Exception e) {
            log.error("Error transcribing audio", e);
            return new TranscriptionResult("", 0, Collections.emptyList());
        }
    }

    /**
     * Generate speech audio using TTS API. Returns raw mp3 bytes.
     */
    public byte[] textToSpeech(String text) {
        if (text == null || text.isBlank()) {
            log.warn("TTS skipped: input text is empty");
            return new byte[0];
        }
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", ttsModel);
            requestBody.put("input", text);
            requestBody.put("voice", ttsVoice);
            requestBody.put("response_format", "mp3");

            String json = objectMapper.writeValueAsString(requestBody);
            Request request = new Request.Builder()
                    .url(baseUrl + "/audio/speech")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("TTS failed: {} {} — {}", response.code(), response.message(), errorBody);
                    return new byte[0];
                }
                return response.body().bytes();
            }
        } catch (Exception e) {
            log.error("Error generating TTS", e);
            return new byte[0];
        }
    }
}
