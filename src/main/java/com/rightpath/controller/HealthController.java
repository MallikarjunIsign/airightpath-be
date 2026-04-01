package com.rightpath.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

@RestController
public class HealthController {

    private static final int MAX_ALLOWED_HEADERS = 2; // adjust limit as needed

    @GetMapping("/actuator/health")
    public Object health(HttpServletRequest request, HttpServletResponse response) throws IOException {

        System.out.println("=== Health Check Request ===");
        System.out.println("Method: " + request.getMethod());
        System.out.println("Request URL: " + request.getRequestURL());

        // Count all headers
        Enumeration<String> headerNames = request.getHeaderNames();
        int headerCount = 0;

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headerCount++;
            System.out.println("Header: " + headerName + " = " + headerValue);
        }

        System.out.println("Total headers received: " + headerCount);

        // Check if count exceeds threshold
        if (headerCount > MAX_ALLOWED_HEADERS) {
            System.out.println("❌ REJECTED: Too many headers (" + headerCount + ")");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            String errorJson = String.format(
                "{\"error\": \"Too many headers\", \"message\": \"Received %d headers (max allowed: %d)\"}",
                headerCount, MAX_ALLOWED_HEADERS
            );
            response.getWriter().write(errorJson);
            return null;
        }

        // Normal OK response
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        String successJson = String.format("{\"status\": \"UP\", \"headerCount\": %d}", headerCount);
        response.getWriter().write(successJson);
        return null;
    }
}