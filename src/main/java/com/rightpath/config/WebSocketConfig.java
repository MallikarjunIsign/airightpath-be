package com.rightpath.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.rightpath.websocket.TerminalSocketHandler;
import com.rightpath.websocket.WebSocketAuthInterceptor;

/**
 * WebSocket configuration class that combines:
 * - STOMP message broker configuration
 * - Raw WebSocket handler configuration
 *
 * <p>Configures two types of WebSocket connections:
 * <ul>
 *   <li>STOMP-based messaging endpoints (for application messaging)</li>
 *   <li>Raw WebSocket connections (for terminal communication)</li>
 * </ul>
 *
 * <p>Security: Uses JWT authentication via WebSocketAuthInterceptor and
 * restricts CORS to configured allowed origins.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocket
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    private final TerminalSocketHandler terminalSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    public WebSocketConfig(
            TerminalSocketHandler terminalSocketHandler,
            WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.terminalSocketHandler = terminalSocketHandler;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue", "/topic");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Configure the underlying Tomcat WebSocket container buffer sizes.
     * Without this, Tomcat defaults to 8 KB which causes 1009 close errors
     * when TTS audio or large STOMP frames are sent.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(1024 * 1024);   // 1 MB
        container.setMaxBinaryMessageBufferSize(1024 * 1024); // 1 MB
        container.setMaxSessionIdleTimeout(0L);               // no idle timeout
        return container;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        logger.info("Registering STOMP endpoint /ws with allowed origins: {}", (Object) allowedOrigins);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(webSocketAuthInterceptor)
                .withSockJS()
                .setWebSocketEnabled(true)
                .setSessionCookieNeeded(false)
                .setStreamBytesLimit(1024 * 1024)     // 1 MB SockJS frame limit
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // 1 MB per STOMP message — sufficient for TTS audio chunks and AI responses
        registration.setMessageSizeLimit(1024 * 1024);
        // 5 MB cumulative send buffer for queued outbound messages
        registration.setSendBufferSizeLimit(5 * 1024 * 1024);
        registration.setSendTimeLimit(60000); // 60 seconds
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        logger.info("Registering WebSocket handler /ws/terminal with allowed origins: {}", (Object) allowedOrigins);

        // Terminal socket with JWT authentication
        registry.addHandler(terminalSocketHandler, "/ws/terminal")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(webSocketAuthInterceptor);
    }
}
