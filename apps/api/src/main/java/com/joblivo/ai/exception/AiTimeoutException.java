package com.joblivo.ai.exception;

/**
 * Thrown when an AI provider client call or model inference execution times out.
 */
public class AiTimeoutException extends AiProviderException {

    public AiTimeoutException(String message) {
        super(message);
    }

    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
