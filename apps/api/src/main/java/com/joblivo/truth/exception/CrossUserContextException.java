package com.joblivo.truth.exception;

/**
 * Thrown when a claim's asserted user boundary conflicts with the factual career context owner.
 * Prevents cross-tenant / cross-user factual validation attacks.
 */
public class CrossUserContextException extends TruthEngineException {

    public CrossUserContextException(String message) {
        super(message);
    }
}
