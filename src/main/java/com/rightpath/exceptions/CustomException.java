package com.rightpath.exceptions;

import org.slf4j.helpers.MessageFormatter;

/**
 * A customizable runtime exception for application-specific error scenarios.
 *
 * <p>This exception serves as a base for domain-specific exceptions and should be used when:
 * <ul>
 *   <li>Business rule validations fail</li>
 *   <li>Domain-specific error conditions occur</li>
 *   <li>You need to propagate errors with detailed context information</li>
 * </ul>
 *
 * <p>This exception supports parameterized messages through SLF4J-style formatting.
 * 
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * throw new CustomException("Invalid user state: {}", user.getState());
 * }</pre>
 */
public class CustomException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with a formatted message.
     *
     * @param message the message pattern (SLF4J-style formatting supported)
     * @param args    arguments for the message pattern
     */
    public CustomException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
    }

    /**
     * Constructs a new exception with a formatted message and root cause.
     *
     * @param message the message pattern (SLF4J-style formatting supported)
     * @param cause   the root cause exception
     * @param args    arguments for the message pattern
     */
    public CustomException(String message, Throwable cause, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage(), cause);
    }

    /**
     * Constructs a new exception with just a root cause.
     *
     * @param cause the root cause exception
     */
    public CustomException(Throwable cause) {
        super(cause);
    }
}