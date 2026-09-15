package com.joblivo.job.exception;

/**
 * Base unchecked exception for Job Discovery and Ingestion domain failures.
 */
public class JobException extends RuntimeException {

    public JobException(String message) {
        super(message);
    }

    public JobException(String message, Throwable cause) {
        super(message, cause);
    }
}
