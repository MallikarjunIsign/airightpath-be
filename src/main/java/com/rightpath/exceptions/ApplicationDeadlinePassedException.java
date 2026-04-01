package com.rightpath.exceptions;

public class ApplicationDeadlinePassedException extends RuntimeException {
    public ApplicationDeadlinePassedException(String message) {
        super(message);
    }
}
