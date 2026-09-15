package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable canonical identity value object for deterministic batch-level deduplication.
 * Uniquely identifies a job candidate within an ingestion batch using only {@code source} and {@code externalJobId}.
 * Strictly excludes semantic or mutable attributes (title, company, description, URL, location, salary).
 */
public record JobBatchIdentity(JobSource source, String externalJobId) {

    public JobBatchIdentity {
        Objects.requireNonNull(source, "JobSource must not be null");
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        String trimmed = externalJobId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("externalJobId must not be blank");
        }
        externalJobId = trimmed;
    }

    /**
     * Converts this batch identity into the canonical domain {@link com.joblivo.job.model.JobSourceIdentity}.
     *
     * @return canonical JobSourceIdentity
     */
    public com.joblivo.job.model.JobSourceIdentity toSourceIdentity() {
        return new com.joblivo.job.model.JobSourceIdentity(source, externalJobId);
    }

    /**
     * Safely constructs an optional {@link JobBatchIdentity} if both source and externalJobId are present and non-blank.
     *
     * @param source        the job source
     * @param externalJobId the raw external job ID
     * @return Optional containing the canonical identity, or empty if inputs are invalid or blank
     */
    public static Optional<JobBatchIdentity> of(JobSource source, String externalJobId) {
        if (source == null || externalJobId == null || externalJobId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new JobBatchIdentity(source, externalJobId));
    }
}
