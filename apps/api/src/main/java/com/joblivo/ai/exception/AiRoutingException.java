package com.joblivo.ai.exception;

/**
 * Thrown when the ModelRouter cannot resolve a valid, enabled AI provider or model for an AI request.
 */
public class AiRoutingException extends AiException {

    public AiRoutingException(String message) {
        super(message);
    }

    public AiRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
