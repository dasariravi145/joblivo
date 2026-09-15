package com.joblivo.job.validation;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable validation outcome representation for Job Discovery candidates.
 * Collects deterministic validation failures without side effects or external dependencies.
 */
public record JobValidationResult(
        boolean isValid,
        List<JobValidationFailure> failures
) {

    public JobValidationResult {
        failures = (failures != null) ? List.copyOf(failures) : List.of();
    }

    /**
     * Creates a successful validation result.
     *
     * @return valid JobValidationResult
     */
    public static JobValidationResult valid() {
        return new JobValidationResult(true, List.of());
    }

    /**
     * Creates an invalid validation result with multiple failures.
     *
     * @param failures list of validation failures
     * @return invalid JobValidationResult
     */
    public static JobValidationResult invalid(List<JobValidationFailure> failures) {
        return new JobValidationResult(false, failures);
    }

    /**
     * Creates an invalid validation result with a single failure.
     *
     * @param field           field name
     * @param failureCategory failure category
     * @param safeMessage     sanitized message
     * @return invalid JobValidationResult
     */
    public static JobValidationResult invalid(String field, String failureCategory, String safeMessage) {
        return new JobValidationResult(false, List.of(new JobValidationFailure(field, failureCategory, safeMessage)));
    }

    /**
     * Returns the primary failure category (from the first failure), or "VALIDATION_ERROR" if valid or empty.
     *
     * @return primary failure category string
     */
    public String primaryFailureCategory() {
        if (failures.isEmpty()) {
            return "VALIDATION_ERROR";
        }
        return failures.get(0).failureCategory();
    }

    /**
     * Returns the first validation failure, if present.
     *
     * @return Optional containing first failure
     */
    public Optional<JobValidationFailure> firstFailure() {
        return failures.isEmpty() ? Optional.empty() : Optional.of(failures.get(0));
    }

    /**
     * Returns a concise, sanitized message summarizing all failures.
     *
     * @return summary message
     */
    public String getSafeMessage() {
        if (failures.isEmpty()) {
            return "Validation succeeded";
        }
        return failures.stream()
                .map(f -> f.field() + ": " + f.safeMessage())
                .collect(Collectors.joining("; "));
    }
}
