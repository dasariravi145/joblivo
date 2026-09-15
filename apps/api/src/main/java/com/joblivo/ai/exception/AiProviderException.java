package com.joblivo.ai.exception;

/**
 * Thrown when an AI provider fails during inference or when no provider client adapter is registered.
 */
public class AiProviderException extends AiException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
