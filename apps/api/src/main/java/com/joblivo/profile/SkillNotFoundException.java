package com.joblivo.profile;

/**
 * Domain exception thrown when a requested skill/technology entry does not exist
 * or does not belong to the user's career profile.
 */
public class SkillNotFoundException extends RuntimeException {

    public SkillNotFoundException(String message) {
        super(message);
    }
}
