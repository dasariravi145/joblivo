package com.joblivo.ai.exception;

/**
 * Thrown when AI Gateway configuration is invalid, missing, or disabled.
 */
public class AiConfigurationException extends AiException {

    public AiConfigurationException(String message) {
        super(message);
    }

    public AiConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
