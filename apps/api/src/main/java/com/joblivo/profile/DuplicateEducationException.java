package com.joblivo.profile;

/**
 * Domain exception thrown when attempting to add or update an education record that already exists
 * in the user's career profile (under normalized institution, degree, and field of study matching).
 */
public class DuplicateEducationException extends RuntimeException {

    public DuplicateEducationException(String message) {
        super(message);
    }

    public DuplicateEducationException(String message, Throwable cause) {
        super(message, cause);
    }
}
