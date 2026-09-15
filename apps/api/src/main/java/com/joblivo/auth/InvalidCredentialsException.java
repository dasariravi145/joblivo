package com.joblivo.auth;

/**
 * Exception thrown when authentication fails due to invalid credentials,
 * inactive account status, or missing authentication identities.
 * Enforces a generic anti-enumeration default message.
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "Invalid email or password";

    public InvalidCredentialsException() {
        super(DEFAULT_MESSAGE);
    }

    public InvalidCredentialsException(String message) {
        super(message != null && !message.isBlank() ? message : DEFAULT_MESSAGE);
    }
}
