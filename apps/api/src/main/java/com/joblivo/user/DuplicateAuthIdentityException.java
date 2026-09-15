package com.joblivo.user;

/**
 * Exception thrown when attempting to create an authentication identity
 * with a provider and subject that already exists in the system.
 */
public class DuplicateAuthIdentityException extends RuntimeException {

    public DuplicateAuthIdentityException(String message) {
        super(message);
    }

    public DuplicateAuthIdentityException(String message, Throwable cause) {
        super(message, cause);
    }
}
