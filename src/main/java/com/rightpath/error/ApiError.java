package com.rightpath.error;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        boolean success,
        String code,
        String message,
        String path,
        Instant timestamp,
        Map<String, Object> details
) {
    public static ApiError of(String code, String message, String path, Map<String, Object> details) {
        return new ApiError(false, code, message, path, Instant.now(), details);
    }
}
