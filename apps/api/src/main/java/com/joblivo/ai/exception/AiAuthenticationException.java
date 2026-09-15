package com.joblivo.ai.exception;

/**
 * Thrown when AI provider authentication, authorization, or credential resolution fails.
 */
public class AiAuthenticationException extends AiProviderException {

    public AiAuthenticationException(String message) {
        super(message);
    }

    public AiAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
