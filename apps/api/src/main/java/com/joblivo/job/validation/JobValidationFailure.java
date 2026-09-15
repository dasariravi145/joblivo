package com.joblivo.job.validation;

import java.util.Objects;

/**
 * Immutable value record representing an individual field-level validation failure
 * identified during candidate ingestion.
 * Strictly avoids sensitive payload leakage or raw exception exposure.
 */
public record JobValidationFailure(
        String field,
        String failureCategory,
        String safeMessage
) {

    public JobValidationFailure {
        field = Objects.requireNonNullElse(field, "unknown");
        failureCategory = (failureCategory != null && !failureCategory.isBlank())
                ? failureCategory.trim()
                : "VALIDATION_ERROR";
        safeMessage = (safeMessage != null && !safeMessage.isBlank())
                ? safeMessage.trim()
                : "Validation failed";
    }
}
