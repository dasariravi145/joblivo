package com.joblivo.profile;

/**
 * Exception thrown when attempting to create a career profile for a user
 * who already possesses a master career profile.
 */
public class DuplicateCareerProfileException extends RuntimeException {

    public DuplicateCareerProfileException(String message) {
        super(message);
    }

    public DuplicateCareerProfileException(String message, Throwable cause) {
        super(message, cause);
    }
}
