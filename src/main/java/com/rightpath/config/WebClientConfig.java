package com.rightpath.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean(name = "genericWebClient")
    public WebClient genericWebClient() {
        return WebClient.builder().build();
    }
}
