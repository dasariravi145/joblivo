package com.joblivo.profile;

/**
 * Domain exception thrown when attempting to add or update a skill that already exists
 * in the user's career profile (under normalized case/whitespace-insensitive matching).
 */
public class DuplicateSkillException extends RuntimeException {

    public DuplicateSkillException(String message) {
        super(message);
    }

    public DuplicateSkillException(String message, Throwable cause) {
        super(message, cause);
    }
}
