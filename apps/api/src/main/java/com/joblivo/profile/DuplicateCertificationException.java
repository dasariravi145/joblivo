package com.joblivo.profile;

/**
 * Domain exception thrown when attempting to add or update a certification that already exists
 * in the user's career profile (under normalized comparison).
 */
public class DuplicateCertificationException extends RuntimeException {

    public DuplicateCertificationException(String message) {
        super(message);
    }

    public DuplicateCertificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
