package com.joblivo.ai.exception;

/**
 * Thrown when an AI provider request is rate-limited, throttled, or exceeds allocated quota.
 */
public class AiRateLimitException extends AiProviderException {

    public AiRateLimitException(String message) {
        super(message);
    }

    public AiRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
