package com.joblivo.user;

/**
 * Exception thrown when attempting to create a credential for an identity
 * that already has an existing credential record.
 */
public class DuplicateCredentialException extends RuntimeException {

    public DuplicateCredentialException(String message) {
        super(message);
    }

    public DuplicateCredentialException(String message, Throwable cause) {
        super(message, cause);
    }
}
