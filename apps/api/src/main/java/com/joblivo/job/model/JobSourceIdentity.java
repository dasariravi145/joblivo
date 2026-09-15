package com.joblivo.job.model;

import com.joblivo.job.exception.JobValidationException;

import java.util.Objects;
import java.util.Optional;

/**
 * Canonical, immutable source identity value object representing a job's origin and external identifier.
 * <p>
 * Uniquely defines a job at the source level using the composite pair: {@code (source, externalJobId)}.
 * Strictly decoupled from semantic, descriptive, or mutable attributes (title, company, description,
 * recruiter, location, salary, or URLs).
 * <p>
 * <strong>Invariants:</strong>
 * <ul>
 *     <li>{@code source} is strictly non-null.</li>
 *     <li>{@code externalJobId} is strictly non-null, non-blank, and trimmed of surrounding whitespace.</li>
 *     <li>Internal characters, punctuation, symbols, and casing in {@code externalJobId} are preserved deterministically.</li>
 *     <li>Equality and hash code depend exclusively on {@code (source, externalJobId)}.</li>
 *     <li>IDs are never fabricated, guessed, or derived from other attributes when absent.</li>
 * </ul>
 */
public record JobSourceIdentity(JobSource source, String externalJobId) {

    public JobSourceIdentity {
        if (source == null) {
            throw new JobValidationException("JobSource must not be null");
        }
        externalJobId = normalizeExternalJobId(externalJobId);
    }

    /**
     * Canonical deterministic normalization rule for external job IDs:
     * <ul>
     *     <li>Trims surrounding whitespace</li>
     *     <li>Preserves case deterministically</li>
     *     <li>Preserves meaningful internal characters and punctuation (e.g. hyphens, slashes, query strings)</li>
     *     <li>Rejects null or blank values strictly</li>
     * </ul>
     *
     * @param rawExternalJobId the raw external identifier
     * @return normalized external identifier
     * @throws JobValidationException if {@code rawExternalJobId} is null or blank
     */
    public static String normalizeExternalJobId(String rawExternalJobId) {
        if (rawExternalJobId == null || rawExternalJobId.isBlank()) {
            throw new JobValidationException("externalJobId must not be null or blank");
        }
        return rawExternalJobId.trim();
    }

    /**
     * Safely constructs an {@link Optional} of {@link JobSourceIdentity} if both {@code source}
     * and {@code externalJobId} are present and non-blank.
     *
     * @param source        the job discovery origin
     * @param externalJobId the raw external identifier
     * @return Optional containing the canonical identity, or empty if inputs are invalid or blank
     */
    public static Optional<JobSourceIdentity> of(JobSource source, String externalJobId) {
        if (source == null || externalJobId == null || externalJobId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new JobSourceIdentity(source, externalJobId));
        } catch (JobValidationException e) {
            return Optional.empty();
        }
    }
}
