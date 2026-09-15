package com.joblivo.profile;

/**
 * Domain exception thrown when attempting to add or update an achievement that already exists
 * in the user's career profile (under normalized comparison).
 */
public class DuplicateAchievementException extends RuntimeException {

    public DuplicateAchievementException(String message) {
        super(message);
    }

    public DuplicateAchievementException(String message, Throwable cause) {
        super(message, cause);
    }
}
