package com.joblivo.job.ingestion;

/**
 * Controlled, low-cardinality categorization of Job Discovery ingestion errors.
 * Ensures metrics and operational diagnostics do not explode tag cardinality.
 */
public enum IngestionErrorCategory {

    /**
     * Candidate structural, constraint, or range validation failures.
     */
    VALIDATION,

    /**
     * Duplicate candidate encountered within the same batch or duplicate source identifier.
     */
    DUPLICATE,

    /**
     * Source adapter fetch failure, timeout, or upstream rate limiting.
     */
    SOURCE_FAILURE,

    /**
     * Database constraint or transactional persistence failure.
     */
    PERSISTENCE,

    /**
     * Configuration or availability error (e.g. source disabled, adapter not found, invalid source).
     */
    CONFIGURATION,

    /**
     * Fatal orchestration or infrastructure error preventing ingestion completion.
     */
    INFRASTRUCTURE,

    /**
     * Unclassified or unexpected processing error.
     */
    UNKNOWN;

    /**
     * Normalizes a raw failure category string to a bounded, low-cardinality enum value.
     *
     * @param rawCategory raw category name from diagnostic failure summaries
     * @return bounded IngestionErrorCategory
     */
    public static IngestionErrorCategory fromRawCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return UNKNOWN;
        }
        return switch (rawCategory.trim().toUpperCase()) {
            case "VALIDATION_ERROR", "INVALID_ARGUMENT", "MISSING_EXTERNAL_ID", "NULL_CANDIDATE", "SOURCE_MISMATCH",
                 "INVALID_EXECUTION_CONTEXT", "VALIDATION", "INVALID_EXTERNAL_JOB_ID", "MISSING_TITLE",
                 "MISSING_COMPANY", "INVALID_JOB_URL", "INVALID_DATE_RANGE", "INVALID_SALARY_RANGE",
                 "INVALID_EXPERIENCE_RANGE", "VALUE_TOO_LONG" -> VALIDATION;
            case "DUPLICATE_IN_BATCH", "DUPLICATE" -> DUPLICATE;
            case "ADAPTER_FETCH_ERROR", "SOURCE_FAILURE" -> SOURCE_FAILURE;
            case "DATA_INTEGRITY_ERROR", "PERSISTENCE" -> PERSISTENCE;
            case "ADAPTER_NOT_FOUND", "SOURCE_DISABLED", "INVALID_SOURCE", "NOT_IMPLEMENTED", "CONFIGURATION" -> CONFIGURATION;
            case "FATAL_ORCHESTRATION_ERROR", "INFRASTRUCTURE" -> INFRASTRUCTURE;
            default -> UNKNOWN;
        };
    }
}
