package com.joblivo.profile;

/**
 * Domain exception thrown when attempting to add or update a project that already exists
 * in the user's career profile (under normalized case/whitespace-insensitive matching).
 */
public class DuplicateProjectException extends RuntimeException {

    public DuplicateProjectException(String message) {
        super(message);
    }

    public DuplicateProjectException(String message, Throwable cause) {
        super(message, cause);
    }
}
