package com.rightpath.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom exception thrown when a user is not found in the database.
 * This can occur during various operations such as login, profile fetch, or update.
 */
public class UserNotFoundDbException extends RuntimeException {

    // Logger for tracking exception creation and details
    private static final Logger logger = LoggerFactory.getLogger(UserNotFoundDbException.class);

    /**
     * Constructs a new UserNotFoundDbException with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception.
     */
    public UserNotFoundDbException(String message) {
        super(message);
        logger.error("UserNotFoundDbException thrown: {}", message);
    }
}
