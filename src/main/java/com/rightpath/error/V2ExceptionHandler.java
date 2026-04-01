package com.rightpath.error;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.rightpath.exceptions.AiServiceException;
import com.rightpath.exceptions.InactiveUserException;
import com.rightpath.exceptions.InvalidAccessTokenException;
import com.rightpath.exceptions.InvalidRefreshTokenException;
import com.rightpath.exceptions.PasswordNotMatchException;
import com.rightpath.exceptions.PasswordSizeException;
import com.rightpath.exceptions.RefreshTokenExpiredException;
import com.rightpath.exceptions.RefreshTokenReuseException;
import com.rightpath.exceptions.ApplicationDeadlinePassedException;
import com.rightpath.exceptions.StorageException;
import com.rightpath.exceptions.UserAlreadyInDatabaseException;
import com.rightpath.exceptions.UserNotFoundDbException;

import jakarta.servlet.http.HttpServletRequest;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.rightpath")
public class V2ExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(V2ExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(V2ErrorCodes.AUTH_BAD_REQUEST, "Validation failed", req.getRequestURI(), Map.of()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCreds(BadCredentialsException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(V2ErrorCodes.AUTH_UNAUTHORIZED, "Invalid username or password", req.getRequestURI(), null));
    }

    @ExceptionHandler(InvalidAccessTokenException.class)
    public ResponseEntity<ApiError> handleInvalidAccess(InvalidAccessTokenException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(V2ErrorCodes.AUTH_INVALID_TOKEN, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler({ InvalidRefreshTokenException.class, RefreshTokenExpiredException.class })
    public ResponseEntity<ApiError> handleInvalidRefresh(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(V2ErrorCodes.AUTH_INVALID_REFRESH, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler(RefreshTokenReuseException.class)
    public ResponseEntity<ApiError> handleReuse(RefreshTokenReuseException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(V2ErrorCodes.AUTH_REFRESH_REUSE_DETECTED, ex.getMessage(), req.getRequestURI(),
                        Map.of("sessionId", ex.getSessionId().toString())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(V2ErrorCodes.AUTH_FORBIDDEN, "Forbidden", req.getRequestURI(), null));
    }

    @ExceptionHandler(UserNotFoundDbException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundDbException ex, HttpServletRequest req) {
        logger.warn("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(V2ErrorCodes.USER_NOT_FOUND, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler(UserAlreadyInDatabaseException.class)
    public ResponseEntity<ApiError> handleUserAlreadyExists(UserAlreadyInDatabaseException ex, HttpServletRequest req) {
        logger.warn("Duplicate user: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(V2ErrorCodes.USER_ALREADY_EXISTS, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler(InactiveUserException.class)
    public ResponseEntity<ApiError> handleInactiveUser(InactiveUserException ex, HttpServletRequest req) {
        logger.warn("Inactive user: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(V2ErrorCodes.USER_INACTIVE, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler(PasswordSizeException.class)
    public ResponseEntity<ApiError> handlePasswordSize(PasswordSizeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(V2ErrorCodes.PASSWORD_POLICY, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler(PasswordNotMatchException.class)
    public ResponseEntity<ApiError> handlePasswordMismatch(PasswordNotMatchException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(V2ErrorCodes.PASSWORD_MISMATCH, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler(ApplicationDeadlinePassedException.class)
    public ResponseEntity<ApiError> handleDeadlinePassed(ApplicationDeadlinePassedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(V2ErrorCodes.APPLICATION_DEADLINE_PASSED, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiError> handleStorage(StorageException ex, HttpServletRequest req) {
        logger.error("Storage error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(V2ErrorCodes.STORAGE_ERROR, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiError> handleAiService(AiServiceException ex, HttpServletRequest req) {
        String message = ex.getMessage();
        String code;
        if (message != null && message.toLowerCase().contains("timed out")) {
            code = V2ErrorCodes.AI_SERVICE_TIMEOUT;
            logger.warn("AI service timeout: {} - path={}", message, req.getRequestURI());
        } else {
            code = V2ErrorCodes.AI_SERVICE_ERROR;
            logger.warn("AI service error: {} - path={}", message, req.getRequestURI());
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(code, message, req.getRequestURI(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        logger.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(V2ErrorCodes.INTERNAL_ERROR, "Internal error", req.getRequestURI(), null));
    }
}
