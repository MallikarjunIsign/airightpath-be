package com.rightpath.error;

/**
 * Machine-readable error codes returned in the {@code code} field of every
 * {@link ApiError}. Clients can switch on these regardless of the human-readable
 * message (which may be localized or reworded).
 */
public final class ErrorCodes {
    private ErrorCodes() {
    }

    public static final String AUTH_BAD_REQUEST = "AUTH_BAD_REQUEST";
    public static final String AUTH_UNAUTHORIZED = "AUTH_UNAUTHORIZED";
    public static final String AUTH_INVALID_TOKEN = "AUTH_INVALID_TOKEN";
    public static final String AUTH_INVALID_REFRESH = "AUTH_INVALID_REFRESH";
    public static final String AUTH_REFRESH_REUSE_DETECTED = "AUTH_REFRESH_REUSE_DETECTED";
    public static final String AUTH_FORBIDDEN = "AUTH_FORBIDDEN";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // Generic request-level
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String CONFLICT = "CONFLICT";

    // Domain
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String USER_ALREADY_EXISTS = "USER_ALREADY_EXISTS";
    public static final String USER_INACTIVE = "USER_INACTIVE";
    public static final String PASSWORD_POLICY = "PASSWORD_POLICY";
    public static final String PASSWORD_MISMATCH = "PASSWORD_MISMATCH";
    public static final String STORAGE_ERROR = "STORAGE_ERROR";
    public static final String APPLICATION_DEADLINE_PASSED = "APPLICATION_DEADLINE_PASSED";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String BUSINESS_ERROR = "BUSINESS_ERROR";
    public static final String COMPILER_ERROR = "COMPILER_ERROR";

    // AI Service
    public static final String AI_SERVICE_TIMEOUT = "AI_SERVICE_TIMEOUT";
    public static final String AI_SERVICE_ERROR = "AI_SERVICE_ERROR";
}
