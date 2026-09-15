package com.joblivo.job.duplicate;

import com.joblivo.job.Job;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of evaluating an incoming candidate against already stored jobs.
 * Encapsulates the deterministic {@link JobDuplicateResult} and the matching persisted {@link Job} (if any).
 *
 * @param result     the deterministic duplicate result
 * @param matchedJob the existing stored Job record that triggered the duplicate detection, or {@code null}
 */
public record StoredJobDuplicateMatch(
        JobDuplicateResult result,
        Job matchedJob
) {

    public StoredJobDuplicateMatch {
        Objects.requireNonNull(result, "result must not be null");
    }

    /**
     * Checks whether this match represents a deterministic duplicate.
     *
     * @return true if duplicate, false otherwise
     */
    public boolean isDuplicate() {
        return result.isDuplicate();
    }

    /**
     * Returns the duplicate classification.
     *
     * @return the duplicate classification
     */
    public JobDuplicateClassification classification() {
        return result.classification();
    }

    /**
     * Returns an Optional wrapping the matched stored job entity.
     *
     * @return Optional containing the matched Job, or empty if not found
     */
    public Optional<Job> matchedJobOptional() {
        return Optional.ofNullable(matchedJob);
    }

    public static StoredJobDuplicateMatch of(JobDuplicateResult result, Job matchedJob) {
        return new StoredJobDuplicateMatch(result, matchedJob);
    }

    public static StoredJobDuplicateMatch notDuplicate(String reason) {
        return new StoredJobDuplicateMatch(JobDuplicateResult.notDuplicate(reason), null);
    }
}
