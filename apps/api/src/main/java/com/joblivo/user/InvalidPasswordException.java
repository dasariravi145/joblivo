package com.joblivo.user;

/**
 * Exception thrown when a provided password fails domain validation rules.
 */
public class InvalidPasswordException extends IllegalArgumentException {

    public InvalidPasswordException(String message) {
        super(message);
    }
}
