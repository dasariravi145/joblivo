package com.joblivo.job.exception;

import com.joblivo.job.validation.JobValidationResult;

import java.util.Optional;

/**
 * Thrown when a job candidate fails structural, range, or temporal validation rules.
 * Preserves low-cardinality failure categories and sanitized error messages.
 */
public class JobValidationException extends JobException {

    private final String failureCategory;
    private final JobValidationResult validationResult;

    public JobValidationException(String message) {
        super(message);
        this.failureCategory = "VALIDATION_ERROR";
        this.validationResult = null;
    }

    public JobValidationException(String failureCategory, String message) {
        super(message);
        this.failureCategory = (failureCategory != null && !failureCategory.isBlank())
                ? failureCategory.trim()
                : "VALIDATION_ERROR";
        this.validationResult = null;
    }

    public JobValidationException(String failureCategory, String message, JobValidationResult validationResult) {
        super(message);
        this.failureCategory = (failureCategory != null && !failureCategory.isBlank())
                ? failureCategory.trim()
                : resolveFailureCategoryFromMessage(message);
        this.validationResult = validationResult;
    }

    public JobValidationException(JobValidationResult validationResult) {
        super(validationResult != null ? validationResult.getSafeMessage() : "Validation failed");
        this.validationResult = validationResult;
        this.failureCategory = validationResult != null ? validationResult.primaryFailureCategory() : "VALIDATION_ERROR";
    }

    public String getFailureCategory() {
        return failureCategory;
    }

    public Optional<JobValidationResult> getValidationResult() {
        return Optional.ofNullable(validationResult);
    }

    private static String resolveFailureCategoryFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return "VALIDATION_ERROR";
        }
        String lower = message.toLowerCase();
        if (lower.contains("title must not be null or blank") || lower.startsWith("title:")) {
            return "MISSING_TITLE";
        }
        if (lower.contains("companyname must not be null or blank") || lower.contains("company name must not be null") || lower.startsWith("companyname:")) {
            return "MISSING_COMPANY";
        }
        if (lower.contains("externaljobid must not be null or blank") || lower.startsWith("externaljobid:")) {
            return "INVALID_EXTERNAL_JOB_ID";
        }
        if (lower.contains("job source must not be null") || lower.startsWith("source:")) {
            return "INVALID_SOURCE";
        }
        if (lower.contains("joburl") || lower.contains("companyurl") || lower.contains("url")) {
            return "INVALID_JOB_URL";
        }
        if (lower.contains("salarymin") || lower.contains("salarymax") || lower.contains("salary")) {
            return "INVALID_SALARY_RANGE";
        }
        if (lower.contains("experienceminyears") || lower.contains("experiencemaxyears") || lower.contains("experience")) {
            return "INVALID_EXPERIENCE_RANGE";
        }
        if (lower.contains("expiresat") || lower.contains("lastseenat") || lower.contains("postedat") || lower.contains("discoveredat")) {
            return "INVALID_DATE_RANGE";
        }
        if (lower.contains("exceed") && lower.contains("characters")) {
            return "VALUE_TOO_LONG";
        }
        return "VALIDATION_ERROR";
    }
}
