package com.rightpath.exceptions;

import org.slf4j.helpers.MessageFormatter;

/**
 * Exception thrown when attempting to create a user that already exists in the system.
 * 
 * <p>This exception typically occurs during:
 * <ul>
 *   <li>User registration with duplicate email/username</li>
 *   <li>Account creation with existing credentials</li>
 *   <li>Data import with conflicting user records</li>
 * </ul>
 * 
 * <p>By convention, this should result in an HTTP 409 (Conflict) or 
 * HTTP 208 (Already Reported) response when handled by Spring controllers.
 * 
 * <p><b>Security Note:</b> Messages should avoid revealing whether specific
 * identifiers (emails/usernames) exist in the system.
 */
public class UserAlreadyInDatabaseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with a formatted message.
     * 
     * @param message the error message pattern (supports SLF4J-style {})
     * @param args arguments for the message pattern
     * 
     * @example 
     * // Generic message recommended for production
     * throw new UserAlreadyInDatabaseException("Account already exists");
     * 
     * // Debug message with parameters (server-side only)
     * throw new UserAlreadyInDatabaseException("User with email {} exists", email);
     */
    public UserAlreadyInDatabaseException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
    }

    /**
     * Factory method for common duplicate user scenarios.
     * 
     * @param identifierType the type of duplicate identifier (e.g., "email", "username")
     * @param identifierValue the duplicate value
     * @return configured exception instance
     */
    public static UserAlreadyInDatabaseException forIdentifier(String identifierType, String identifierValue) {
        return new UserAlreadyInDatabaseException(
            "User with {} '{}' already exists", identifierType, identifierValue);
    }
}