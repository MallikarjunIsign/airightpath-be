package com.rightpath.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Configuration
public class OpenAIConfig {

	@Value("${openai.api.key}")
	private String openAiApiKey;

	@Value("${openai.api.base-url}")
	private String openAiBaseUrl;

	@Value("${openai.question.timeout-seconds:180}")
	private int questionTimeoutSeconds;

	@Bean(name = "openAiWebClient")
	public WebClient openAiWebClient() {
		HttpClient httpClient = HttpClient.create()
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
				.responseTimeout(Duration.ofSeconds(questionTimeoutSeconds));

		return WebClient.builder()
				.baseUrl(openAiBaseUrl)
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
				.filter(logRequest())
				.build();
	}

	private ExchangeFilterFunction logRequest() {
		return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
			org.slf4j.LoggerFactory.getLogger(OpenAIConfig.class)
					.info("OpenAI Request: {} {}", clientRequest.method(), clientRequest.url());
			return Mono.just(clientRequest);
		});
	}
}
