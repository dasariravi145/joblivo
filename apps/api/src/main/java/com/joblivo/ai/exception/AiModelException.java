package com.joblivo.ai.exception;

/**
 * Thrown when a requested AI model is unavailable, not found, or inaccessible on the provider backend.
 */
public class AiModelException extends AiProviderException {

    public AiModelException(String message) {
        super(message);
    }

    public AiModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
