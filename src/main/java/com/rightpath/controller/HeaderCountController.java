package com.rightpath.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;

@RestController
public class HeaderCountController {

    @GetMapping("/header-count")
    public ResponseEntity<String> getHeaderCount(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        int customHeaderCount = 0;

        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            // Count only your custom headers (e.g. ones starting with X-)
            if (header.toLowerCase().startsWith("x-")) {
                customHeaderCount++;
            }
        }

        if (customHeaderCount == 1) {
            return ResponseEntity.ok("✅ Request accepted. Custom header count: " + customHeaderCount);
        } else {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body("❌ Invalid request found — multiple or missing custom headers. Count: " + customHeaderCount);
        }
    }
}


