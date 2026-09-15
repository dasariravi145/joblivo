package com.joblivo.profile;

/**
 * Domain exception thrown when a requested achievement entry does not exist
 * or does not belong to the user's career profile.
 */
public class AchievementNotFoundException extends RuntimeException {

    public AchievementNotFoundException(String message) {
        super(message);
    }
}
