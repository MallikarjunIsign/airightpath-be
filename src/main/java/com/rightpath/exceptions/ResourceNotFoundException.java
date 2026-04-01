package com.rightpath.exceptions;

import org.slf4j.helpers.MessageFormatter;

/**
 * Exception thrown when a requested resource cannot be found in the system.
 * 
 * <p>This exception should be used when:
 * <ul>
 *   <li>Database entities are not found by ID</li>
 *   <li>Files or external resources are missing</li>
 *   <li>API endpoints request non-existent resources</li>
 * </ul>
 * 
 * <p>By convention, this results in an HTTP 404 (Not Found) response when handled
 * by Spring's exception handlers.
 */
public class ResourceNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new resource not found exception with formatted message.
     * 
     * @param message the error message pattern (supports SLF4J-style {})
     * @param args arguments for the message pattern
     * 
     * @example 
     * throw new ResourceNotFoundException("User with id {} not found", userId);
     */
    public ResourceNotFoundException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
    }

    /**
     * Constructs a new resource not found exception with formatted message and cause.
     * 
     * @param message the error message pattern
     * @param cause the root cause exception
     * @param args arguments for the message pattern
     */
    public ResourceNotFoundException(String message, Throwable cause, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage(), cause);
    }

    /**
     * Factory method for common "not found by ID" scenarios.
     * 
     * @param resourceType the type of resource (e.g., "User", "Order")
     * @param id the ID that wasn't found
     * @return configured exception instance
     */
    public static ResourceNotFoundException forId(Class<?> resourceType, Object id) {
        return new ResourceNotFoundException("{} with id {} not found", 
            resourceType.getSimpleName(), id);
    }
}