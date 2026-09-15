package com.joblivo.ai.exception;

/**
 * Base runtime exception for all AI Gateway operations in Joblivo.
 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
