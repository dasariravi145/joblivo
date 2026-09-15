package com.joblivo.profile;

/**
 * Domain exception thrown when a requested work experience entry does not exist
 * or does not belong to the user's career profile.
 */
public class WorkExperienceNotFoundException extends RuntimeException {

    public WorkExperienceNotFoundException(String message) {
        super(message);
    }
}
