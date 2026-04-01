package com.rightpath.exceptions;

import org.slf4j.helpers.MessageFormatter;

/**
 * Exception thrown when password validation fails during authentication or password change operations.
 * 
 * <p>This exception indicates that the provided password does not meet system requirements,
 * either because:
 * <ul>
 *   <li>Password doesn't match stored credentials during login</li>
 *   <li>New password doesn't meet complexity requirements</li>
 *   <li>Password confirmation doesn't match during registration/password reset</li>
 * </ul>
 * 
 * <p>Security Note: Error messages should not reveal specific validation rules to prevent
 * information leakage to potential attackers.
 */
public class PasswordNotMatchException extends SecurityException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with a detailed message.
     * 
     * @param message The error description (using SLF4J-style {} formatting)
     * @param args Arguments for the formatted message
     * 
     * @example 
     * new PasswordNotMatchException("Password validation failed for user {}", username)
     */
    public PasswordNotMatchException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
    }

    /**
     * Constructs a new exception with a detailed message and root cause.
     * 
     * @param message The error description
     * @param cause The underlying exception
     * @param args Arguments for the formatted message
     */
    public PasswordNotMatchException(String message, Throwable cause, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage(), cause);
    }
}