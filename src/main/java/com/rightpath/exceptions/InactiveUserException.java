package com.rightpath.exceptions;

import org.slf4j.helpers.MessageFormatter;

/**
 * Exception thrown when attempting to authenticate or authorize an inactive user account.
 *
 * <p>This exception indicates that while the credentials may be valid, the account
 * is not currently active and thus cannot be used to access protected resources.
 *
 * <p>Common scenarios include:
 * <ul>
 *   <li>Account deactivation by administrator</li>
 *   <li>Temporary suspension</li>
 *   <li>Pending email verification</li>
 *   <li>Expired trial accounts</li>
 * </ul>
 *
 * <p>This exception supports parameterized messages through SLF4J-style formatting.
 */
public class InactiveUserException extends SecurityException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with a detailed message.
     *
     * @param message the description of the inactive state (SLF4J-style formatting supported)
     * @param args arguments for the message template
     */
    public InactiveUserException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
    }

    /**
     * Constructs a new exception with a detailed message and root cause.
     *
     * @param message the description of the inactive state
     * @param cause the underlying exception that caused this state
     * @param args arguments for the message template
     */
    public InactiveUserException(String message, Throwable cause, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage(), cause);
    }
}