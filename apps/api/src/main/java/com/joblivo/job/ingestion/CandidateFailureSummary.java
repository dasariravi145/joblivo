package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;

import java.util.Objects;

/**
 * Safe, immutable diagnostic summary of an individual candidate ingestion failure.
 * Strictly avoids exposing full job descriptions, user data, credentials, or sensitive payloads.
 */
public record CandidateFailureSummary(
        JobSource source,
        String externalJobId,
        String failureCategory,
        String safeMessage
) {

    public CandidateFailureSummary {
        failureCategory = (failureCategory != null && !failureCategory.isBlank())
                ? failureCategory.trim()
                : "UNKNOWN_FAILURE";
        safeMessage = (safeMessage != null && !safeMessage.isBlank())
                ? safeMessage.trim()
                : "Operation failed without detailed error message";
        externalJobId = (externalJobId != null && !externalJobId.isBlank())
                ? externalJobId.trim()
                : null;
    }
}
