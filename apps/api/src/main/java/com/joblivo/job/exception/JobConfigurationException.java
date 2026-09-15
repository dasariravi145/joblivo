package com.joblivo.job.exception;

/**
 * Thrown when Job Discovery configuration is invalid, malformed, or exceeds safety bounds.
 */
public class JobConfigurationException extends JobException {

    public JobConfigurationException(String message) {
        super(message);
    }

    public JobConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
