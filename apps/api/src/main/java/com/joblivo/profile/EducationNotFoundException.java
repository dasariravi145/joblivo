package com.joblivo.profile;

/**
 * Domain exception thrown when a requested education record does not exist
 * or does not belong to the user's career profile.
 */
public class EducationNotFoundException extends RuntimeException {

    public EducationNotFoundException(String message) {
        super(message);
    }
}
