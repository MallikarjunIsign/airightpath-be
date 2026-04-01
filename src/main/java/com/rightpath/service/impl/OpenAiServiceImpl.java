package com.rightpath.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rightpath.dto.ChatCompletionRequest;
import com.rightpath.dto.ChatCompletionResponse;
import com.rightpath.dto.CodingQuestion;
import com.rightpath.dto.OpenAiResponse;
import com.rightpath.dto.Question;
import com.rightpath.enums.PromptType;
import com.rightpath.exceptions.AiServiceException;
import com.rightpath.repository.JobPromptRepository;
import com.rightpath.service.OpenAiService;
import com.rightpath.util.PromptPlaceholderResolver;

import reactor.util.retry.Retry;

@Service
public class OpenAiServiceImpl implements OpenAiService {

	private static final Logger log = LoggerFactory.getLogger(OpenAiServiceImpl.class);

	private final WebClient webClient;
	@Value("${openai.model}")
	private String model;
	@Value("${openai.question.timeout-seconds:180}")
	private int questionTimeoutSeconds;
	private final ObjectMapper objectMapper = JsonMapper.builder()
			.enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
			.build();
	private final JobPromptRepository jobPromptRepository;
	private final PromptPlaceholderResolver placeholderResolver;

	public OpenAiServiceImpl(@Qualifier("openAiWebClient") WebClient webClient,
			JobPromptRepository jobPromptRepository,
			PromptPlaceholderResolver placeholderResolver) {
		this.webClient = webClient;
		this.jobPromptRepository = jobPromptRepository;
		this.placeholderResolver = placeholderResolver;
	}

	@Override
	public String ask(String conversationHistory) {

		ChatCompletionRequest request = new ChatCompletionRequest();
		request.setModel(model);

		request.setMessages(List.of(Map.of("role", "user", "content", conversationHistory)));

		ChatCompletionResponse response = webClient.post().uri("/chat/completions").bodyValue(request).retrieve()
				.bodyToMono(ChatCompletionResponse.class).timeout(Duration.ofSeconds(90))
				.retryWhen(Retry.backoff(3, Duration.ofSeconds(2)).filter(this::isRetryable))
				.onErrorMap(ex -> ex instanceof TimeoutException,
						ex -> new AiServiceException("AI response timed out, please retry"))
				.onErrorMap(reactor.core.Exceptions::isRetryExhausted,
						ex -> new AiServiceException("AI service unavailable after retries, please try again later", ex))
				.block();

		if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
			throw new RuntimeException("Empty response from OpenAI");
		}

		return response.getChoices().get(0).getMessage().getContent();
	}

	private boolean isRetryable(Throwable t) {
		if (t instanceof WebClientResponseException ex) {
			boolean retry = ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 429;
			if (retry) {
				log.warn("Retryable error from OpenAI: status={}, message={}", ex.getStatusCode().value(),
						ex.getMessage());
			}
			return retry;
		}
		return false;
	}

	@Override
	public List<Question> generateQuestions(String jobPrefix) {
		log.info("generateQuestions START - jobPrefix={}", jobPrefix);
		long start = System.currentTimeMillis();

		String prompt = jobPromptRepository.findByJobPrefixAndPromptType(jobPrefix, PromptType.APTITUDE)
				.orElseThrow(() -> new AiServiceException("Aptitude prompt not configured for jobPrefix=" + jobPrefix))
				.getPrompt();
		prompt = placeholderResolver.resolveJobPlaceholders(prompt, jobPrefix);
		log.info("generateQuestions - prompt loaded, length={}, took={}ms", prompt.length(),
				System.currentTimeMillis() - start);

		long apiStart = System.currentTimeMillis();
		log.info("generateQuestions - calling OpenAI API (model=gpt-5-mini, timeout={}s) ...", questionTimeoutSeconds);

		OpenAiResponse response = webClient.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("model", "gpt-5-mini", "messages",
						new Object[] { Map.of("role", "user", "content", prompt) }))
				.retrieve().bodyToMono(OpenAiResponse.class)
				.timeout(Duration.ofSeconds(questionTimeoutSeconds))
				.retryWhen(Retry.backoff(2, Duration.ofSeconds(3)).filter(this::isRetryable))
				.onErrorMap(ex -> ex instanceof TimeoutException,
						ex -> new AiServiceException("AI question generation timed out, please retry"))
				.onErrorMap(reactor.core.Exceptions::isRetryExhausted,
						ex -> new AiServiceException("AI service unavailable after retries, please try again later", ex))
				.block();

		log.info("generateQuestions - OpenAI API responded, took={}ms", System.currentTimeMillis() - apiStart);

		if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
			log.error("generateQuestions - Empty response from OpenAI, took={}ms total",
					System.currentTimeMillis() - start);
			throw new AiServiceException("Empty response from OpenAI");
		}

		String content = cleanJson(response.getChoices().get(0).getMessage().getContent());
		log.info("generateQuestions - response content length={}", content.length());

		try {
			List<Question> questions = objectMapper.readValue(content, new TypeReference<List<Question>>() {
			});
			log.info("generateQuestions END - parsed {} questions, total took={}ms", questions.size(),
					System.currentTimeMillis() - start);
			return questions;
		} catch (Exception e) {
			log.warn("generateQuestions - Failed to parse JSON, took={}ms", System.currentTimeMillis() - start);
			throw new AiServiceException("Failed to parse AI response, please retry");
		}
	}

	@Override
	public List<CodingQuestion> generateCodingQuestions(String jobPrefix) {
		log.info("generateCodingQuestions START - jobPrefix={}", jobPrefix);
		long start = System.currentTimeMillis();

		String prompt = jobPromptRepository.findByJobPrefixAndPromptType(jobPrefix, PromptType.CODING)
				.orElseThrow(() -> new AiServiceException("Coding prompt not configured for jobPrefix=" + jobPrefix))
				.getPrompt();
		prompt = placeholderResolver.resolveJobPlaceholders(prompt, jobPrefix);
		log.info("generateCodingQuestions - prompt loaded, length={}, took={}ms", prompt.length(),
				System.currentTimeMillis() - start);

		long apiStart = System.currentTimeMillis();
		log.info("generateCodingQuestions - calling OpenAI API (model=gpt-5, timeout={}s) ...", questionTimeoutSeconds);

		OpenAiResponse response = webClient.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("model", "gpt-5", "messages",
						new Object[] { Map.of("role", "user", "content", prompt) }))
				.retrieve().bodyToMono(OpenAiResponse.class)
				.timeout(Duration.ofSeconds(questionTimeoutSeconds))
				.retryWhen(Retry.backoff(2, Duration.ofSeconds(3)).filter(this::isRetryable))
				.onErrorMap(ex -> ex instanceof TimeoutException,
						ex -> new AiServiceException("AI coding question generation timed out, please retry"))
				.onErrorMap(reactor.core.Exceptions::isRetryExhausted,
						ex -> new AiServiceException("AI service unavailable after retries, please try again later", ex))
				.block();

		log.info("generateCodingQuestions - OpenAI API responded, took={}ms", System.currentTimeMillis() - apiStart);

		if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
			throw new AiServiceException("Empty response from OpenAI");
		}

		String content = cleanJson(response.getChoices().get(0).getMessage().getContent());
		log.info("generateCodingQuestions - response content length={}", content.length());

		try {
			List<CodingQuestion> questions = objectMapper.readValue(content,
					new TypeReference<List<CodingQuestion>>() {
					});
			log.info("generateCodingQuestions END - parsed {} questions, total took={}ms", questions.size(),
					System.currentTimeMillis() - start);
			return questions;
		} catch (Exception e) {
			log.warn("generateCodingQuestions - Failed to parse JSON, took={}ms", System.currentTimeMillis() - start);
			throw new AiServiceException("Failed to parse AI response, please retry");
		}
	}

	private String cleanJson(String content) {

		content = content.trim();

		if (content.contains("```json")) {
			content = content.substring(content.indexOf("```json") + 7);
		}

		if (content.contains("```")) {
			content = content.substring(0, content.indexOf("```"));
		}

		int firstBracket = content.indexOf("[");
		int lastBracket = content.lastIndexOf("]");

		if (firstBracket >= 0 && lastBracket > firstBracket) {
			content = content.substring(firstBracket, lastBracket + 1);
		}

		// Remove invalid JSON escape sequences like \$ that OpenAI sometimes produces. 
		// This replaces \X (where X is not a valid escape char) with just X.
		content = content.replaceAll("\\\\([^\"\\\\bfnrtu/])", "$1");

		return content;
	}
}
