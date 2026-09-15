package com.joblivo.truth.exception;

/**
 * Thrown when the supplied career context is structurally invalid (e.g. null, missing user ID).
 */
public class InvalidCareerContextException extends TruthEngineException {

    public InvalidCareerContextException(String message) {
        super(message);
    }
}
