package com.joblivo.profile;

/**
 * Domain exception thrown when a requested certification entry does not exist
 * or does not belong to the user's career profile.
 */
public class CertificationNotFoundException extends RuntimeException {

    public CertificationNotFoundException(String message) {
        super(message);
    }
}
