package com.joblivo.truth.exception;

/**
 * Thrown when an evaluated claim is structurally invalid (e.g. null, blank text, missing category).
 */
public class InvalidClaimException extends TruthEngineException {

    public InvalidClaimException(String message) {
        super(message);
    }
}
