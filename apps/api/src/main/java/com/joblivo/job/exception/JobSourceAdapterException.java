package com.joblivo.job.exception;

import com.joblivo.job.ingestion.IngestionErrorCategory;
import com.joblivo.job.model.JobSource;

import java.util.Objects;

/**
 * Unchecked domain exception thrown when a {@link com.joblivo.job.ingestion.JobSourceAdapter} encounters an unrecoverable failure
 * during candidate retrieval.
 * <p>
 * Enforces architectural and security boundaries by communicating:
 * <ul>
 *   <li>The originating {@link JobSource}</li>
 *   <li>A structured, low-cardinality {@link IngestionErrorCategory} (defaulting to {@link IngestionErrorCategory#SOURCE_FAILURE})</li>
 *   <li>A safe, sanitized diagnostic message free of credentials, tokens, authorization headers, or raw payload dumps</li>
 * </ul>
 */
public class JobSourceAdapterException extends JobException {

    private final JobSource source;
    private final IngestionErrorCategory errorCategory;

    public JobSourceAdapterException(JobSource source, String message) {
        this(source, IngestionErrorCategory.SOURCE_FAILURE, message, null);
    }

    public JobSourceAdapterException(JobSource source, String message, Throwable cause) {
        this(source, IngestionErrorCategory.SOURCE_FAILURE, message, cause);
    }

    public JobSourceAdapterException(JobSource source, IngestionErrorCategory errorCategory, String message) {
        this(source, errorCategory, message, null);
    }

    public JobSourceAdapterException(JobSource source, IngestionErrorCategory errorCategory, String message, Throwable cause) {
        super(sanitize(message), cause);
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.errorCategory = errorCategory != null ? errorCategory : IngestionErrorCategory.SOURCE_FAILURE;
    }

    /**
     * The job source from which the failure originated.
     */
    public JobSource getSource() {
        return source;
    }

    /**
     * The categorized error classification for low-cardinality observability and metrics tracking.
     */
    public IngestionErrorCategory getErrorCategory() {
        return errorCategory;
    }

    /**
     * Sanitizes diagnostic messages to prevent credential, secret, or token leakage.
     */
    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Job source adapter execution failed";
        }
        String result = message.trim();
        result = result.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+", "$1[REDACTED]");
        result = result.replaceAll("(?i)(password|secret|token|apiKey|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]");
        return result;
    }
}
