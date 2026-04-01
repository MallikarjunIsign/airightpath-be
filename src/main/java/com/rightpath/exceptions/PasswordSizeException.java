package com.rightpath.exceptions;

import org.slf4j.helpers.MessageFormatter;

/**
 * Exception thrown when a password fails to meet size/length requirements during
 * validation.
 *
 * <p>This exception indicates password policy violations related to length constraints,
 * typically during:
 * <ul>
 *   <li>User registration</li>
 *   <li>Password changes</li>
 *   <li>Password resets</li>
 * </ul>
 *
 * <p><b>Security Note:</b> Messages should avoid revealing specific password policy
 * details to prevent attackers from gaining system intelligence.
 */
public class PasswordSizeException extends SecurityException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new password size exception with formatted message.
     *
     * @param message the error message pattern (supports SLF4J-style {})
     * @param args arguments for the message pattern
     *
     * @example 
     * // Generic message recommended for production
     * throw new PasswordSizeException("Password does not meet requirements");
     */
    public PasswordSizeException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
    }

    /**
     * Constructs a new password size exception with formatted message and root cause.
     *
     * @param message the error message pattern
     * @param cause the underlying exception
     * @param args arguments for the message pattern
     */
    public PasswordSizeException(String message, Throwable cause, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage(), cause);
    }
}