package com.joblivo.truth.exception;

/**
 * Base unchecked exception for Truth Engine system failures and boundary violations.
 * Note: An unsupported claim is a normal business evaluation result, NOT a system failure.
 */
public class TruthEngineException extends RuntimeException {

    public TruthEngineException(String message) {
        super(message);
    }

    public TruthEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
