package com.joblivo.job.duplicate;

import java.util.Objects;

/**
 * Immutable outcome of a deterministic duplicate comparison between two job representations.
 * Encapsulates the resolved classification, a safe diagnostic explanation, and the matching evidence key.
 *
 * @param classification the resolved duplicate classification
 * @param reason         safe, human-readable reason for the classification
 * @param evidenceDetail the matching signal value (e.g. source identity, canonical URL, or SHA-256 hash)
 */
public record JobDuplicateResult(
        JobDuplicateClassification classification,
        String reason,
        String evidenceDetail
) {

    public JobDuplicateResult {
        Objects.requireNonNull(classification, "classification must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }

    /**
     * Checks whether this result represents a deterministic duplicate.
     *
     * @return true if duplicate, false otherwise
     */
    public boolean isDuplicate() {
        return classification.isDuplicate();
    }

    public static JobDuplicateResult exactSourceDuplicate(String reason, String evidenceDetail) {
        return new JobDuplicateResult(JobDuplicateClassification.EXACT_SOURCE_DUPLICATE, reason, evidenceDetail);
    }

    public static JobDuplicateResult exactUrlDuplicate(String reason, String evidenceDetail) {
        return new JobDuplicateResult(JobDuplicateClassification.EXACT_URL_DUPLICATE, reason, evidenceDetail);
    }

    public static JobDuplicateResult exactContentDuplicate(String reason, String evidenceDetail) {
        return new JobDuplicateResult(JobDuplicateClassification.EXACT_CONTENT_DUPLICATE, reason, evidenceDetail);
    }

    public static JobDuplicateResult notDuplicate(String reason) {
        return new JobDuplicateResult(JobDuplicateClassification.NOT_DUPLICATE, reason, null);
    }
}
