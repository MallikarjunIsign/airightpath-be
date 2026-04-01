package com.rightpath.error;

public final class V2ErrorCodes {
    private V2ErrorCodes() {
    }

    public static final String AUTH_BAD_REQUEST = "AUTH_BAD_REQUEST";
    public static final String AUTH_UNAUTHORIZED = "AUTH_UNAUTHORIZED";
    public static final String AUTH_INVALID_TOKEN = "AUTH_INVALID_TOKEN";
    public static final String AUTH_INVALID_REFRESH = "AUTH_INVALID_REFRESH";
    public static final String AUTH_REFRESH_REUSE_DETECTED = "AUTH_REFRESH_REUSE_DETECTED";
    public static final String AUTH_FORBIDDEN = "AUTH_FORBIDDEN";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // Domain
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String USER_ALREADY_EXISTS = "USER_ALREADY_EXISTS";
    public static final String USER_INACTIVE = "USER_INACTIVE";
    public static final String PASSWORD_POLICY = "PASSWORD_POLICY";
    public static final String PASSWORD_MISMATCH = "PASSWORD_MISMATCH";
    public static final String STORAGE_ERROR = "STORAGE_ERROR";
    public static final String APPLICATION_DEADLINE_PASSED = "APPLICATION_DEADLINE_PASSED";

    // AI Service
    public static final String AI_SERVICE_TIMEOUT = "AI_SERVICE_TIMEOUT";
    public static final String AI_SERVICE_ERROR = "AI_SERVICE_ERROR";
}
