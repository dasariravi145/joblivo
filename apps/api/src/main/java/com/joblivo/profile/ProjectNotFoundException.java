package com.joblivo.profile;

/**
 * Domain exception thrown when a requested project entry does not exist
 * or does not belong to the user's career profile.
 */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String message) {
        super(message);
    }
}
