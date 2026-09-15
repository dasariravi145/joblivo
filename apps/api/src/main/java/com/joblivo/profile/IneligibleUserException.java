package com.joblivo.profile;

/**
 * Exception thrown when a user's account status disqualifies them from owning
 * a master career profile (e.g. SUSPENDED or DELETED accounts).
 */
public class IneligibleUserException extends IllegalArgumentException {

    public IneligibleUserException(String message) {
        super(message);
    }
}
