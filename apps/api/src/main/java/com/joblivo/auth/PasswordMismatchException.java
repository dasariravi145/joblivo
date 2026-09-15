package com.joblivo.auth;

/**
 * Exception thrown when the supplied password and confirmation password do not match.
 */
public class PasswordMismatchException extends IllegalArgumentException {

    public PasswordMismatchException(String message) {
        super(message);
    }
}
