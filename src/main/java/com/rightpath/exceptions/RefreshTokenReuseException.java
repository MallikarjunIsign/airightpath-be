package com.rightpath.exceptions;

import java.util.UUID;

public class RefreshTokenReuseException extends RuntimeException {

    private final UUID sessionId;

    public RefreshTokenReuseException(String message, UUID sessionId) {
        super(message);
        this.sessionId = sessionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
