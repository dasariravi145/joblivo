package com.joblivo.profile;

/**
 * Domain exception thrown when a requested Master Career Profile does not exist for an eligible user.
 */
public class CareerProfileNotFoundException extends RuntimeException {

    public CareerProfileNotFoundException(String message) {
        super(message);
    }

    public CareerProfileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
