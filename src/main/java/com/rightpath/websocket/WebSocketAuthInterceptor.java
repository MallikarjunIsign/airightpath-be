package com.rightpath.websocket;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.rightpath.exceptions.InvalidAccessTokenException;
import com.rightpath.service.impl.AccessTokenService;

import io.jsonwebtoken.Claims;

/**
 * WebSocket handshake interceptor that validates JWT tokens.
 *
 * Token can be provided via:
 * 1. Query parameter: ?token=xxx
 * 2. Authorization header: Bearer xxx
 *
 * On successful authentication, user info is stored in WebSocket session attributes.
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    public static final String USER_EMAIL_ATTR = "userEmail";
    public static final String USER_AUTHORITIES_ATTR = "userAuthorities";
    public static final String AUTHENTICATED_ATTR = "authenticated";

    private final AccessTokenService accessTokenService;

    public WebSocketAuthInterceptor(AccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String token = extractToken(request);

        if (token == null || token.isBlank()) {
            log.warn("WebSocket connection rejected: No token provided. URI={}", request.getURI());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Claims claims = accessTokenService.validateAndGetClaims(token);
            String userEmail = claims.getSubject();
            String authorities = (String) claims.get("authorities");

            // Store user info in session attributes for use by handlers
            attributes.put(USER_EMAIL_ATTR, userEmail);
            attributes.put(USER_AUTHORITIES_ATTR, authorities != null ? authorities : "");
            attributes.put(AUTHENTICATED_ATTR, true);

            log.info("WebSocket connection authenticated for user: {}", userEmail);
            return true;

        } catch (InvalidAccessTokenException e) {
            log.warn("WebSocket connection rejected: Invalid token. URI={}, reason={}",
                    request.getURI(), e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } catch (Exception e) {
            log.error("WebSocket authentication error. URI={}", request.getURI(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No action needed after handshake
    }

    /**
     * Extracts JWT token from request.
     * Priority: 1) Query parameter 'token', 2) Authorization header
     */
    private String extractToken(ServerHttpRequest request) {
        // Try query parameter first (preferred for WebSocket)
        String query = request.getURI().getQuery();
        if (query != null) {
            var params = UriComponentsBuilder.fromUriString("?" + query).build().getQueryParams();
            String token = params.getFirst("token");
            if (token != null && !token.isBlank()) {
                return token;
            }
        }

        // Try Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // For SockJS connections, try to get from servlet request
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token != null && !token.isBlank()) {
                return token;
            }
        }

        return null;
    }
}
