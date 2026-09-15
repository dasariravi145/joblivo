package com.joblivo.job.model;

import com.joblivo.job.Job;
import com.joblivo.job.normalizer.NormalizedJobCandidate;
import com.joblivo.job.service.JobResponse;

import java.time.Instant;
import java.util.Objects;

/**
 * Canonical immutable representation of a job's source origin and ingestion provenance.
 * <p>
 * Retains sufficient provenance to deterministically establish:
 * <ul>
 *   <li><strong>Where the job originated:</strong> {@link #source()}</li>
 *   <li><strong>Which external source identified it:</strong> {@link #sourceIdentity()} composite pair</li>
 *   <li><strong>Which external job identifier belongs to that source:</strong> {@link #externalJobId()}</li>
 *   <li><strong>How the job can be reached:</strong> {@link #jobUrl()}, {@link #companyUrl()}, and {@link #applicationMethod()}</li>
 *   <li><strong>When Joblivo discovered and last observed it:</strong> {@link #discoveredAt()} and {@link #lastSeenAt()},
 *       along with source-provided {@link #postedAt()} and {@link #expiresAt()}</li>
 * </ul>
 * <p>
 * <strong>Invariants &amp; Safety Guarantees:</strong>
 * <ul>
 *   <li>{@code source} is strictly non-null and strongly typed via {@link JobSource}.</li>
 *   <li>{@code externalJobId} is normalized via {@link JobSourceIdentity#normalizeExternalJobId(String)} (trimmed, case/punctuation preserved, strictly non-blank).</li>
 *   <li>Identity is strictly deterministic: {@code (source, externalJobId)}. Identifiers are never treated as globally unique.</li>
 *   <li>Zero timestamp fabrication: {@code postedAt} and {@code expiresAt} remain {@code null} if not provided by source.</li>
 *   <li>Zero URL fetching: URLs are preserved as normalized text without HTTP requests, DNS resolution, or redirect following (zero SSRF).</li>
 *   <li>Zero credentials: OAuth tokens, cookies, passwords, authorization headers, or crawler internals are never stored or exposed.</li>
 * </ul>
 */
public record JobSourceProvenance(
        JobSource source,
        String externalJobId,
        String jobUrl,
        String companyUrl,
        Instant postedAt,
        Instant expiresAt,
        Instant discoveredAt,
        Instant lastSeenAt,
        JobApplicationMethod applicationMethod
) {

    public JobSourceProvenance {
        Objects.requireNonNull(source, "JobSource must not be null");
        externalJobId = JobSourceIdentity.normalizeExternalJobId(externalJobId);
        applicationMethod = applicationMethod != null ? applicationMethod : JobApplicationMethod.UNKNOWN;
    }

    /**
     * Resolves the canonical, immutable {@link JobSourceIdentity} composite pair {@code (source, externalJobId)}.
     *
     * @return canonical JobSourceIdentity
     */
    public JobSourceIdentity sourceIdentity() {
        return new JobSourceIdentity(source, externalJobId);
    }

    /**
     * Extracts canonical source provenance from a persisted {@link Job} entity.
     *
     * @param job the job entity
     * @return canonical JobSourceProvenance
     */
    public static JobSourceProvenance from(Job job) {
        Objects.requireNonNull(job, "Job must not be null");
        return new JobSourceProvenance(
                job.getSource(),
                job.getExternalJobId(),
                job.getJobUrl(),
                job.getCompanyUrl(),
                job.getPostedAt(),
                job.getExpiresAt(),
                job.getDiscoveredAt(),
                job.getLastSeenAt(),
                job.getApplicationMethod()
        );
    }

    /**
     * Extracts canonical source provenance from an immutable {@link JobResponse} read model.
     *
     * @param response the job read model
     * @return canonical JobSourceProvenance
     */
    public static JobSourceProvenance from(JobResponse response) {
        Objects.requireNonNull(response, "JobResponse must not be null");
        return new JobSourceProvenance(
                response.source(),
                response.externalJobId(),
                response.jobUrl(),
                response.companyUrl(),
                response.postedAt(),
                response.expiresAt(),
                response.discoveredAt(),
                response.lastSeenAt(),
                response.applicationMethod()
        );
    }

    /**
     * Extracts canonical source provenance from a {@link NormalizedJobCandidate}.
     *
     * @param candidate the normalized candidate
     * @return canonical JobSourceProvenance
     */
    public static JobSourceProvenance from(NormalizedJobCandidate candidate) {
        Objects.requireNonNull(candidate, "NormalizedJobCandidate must not be null");
        return new JobSourceProvenance(
                candidate.source(),
                candidate.externalJobId(),
                candidate.jobUrl(),
                candidate.companyUrl(),
                candidate.postedAt(),
                candidate.expiresAt(),
                candidate.discoveredAt(),
                candidate.lastSeenAt(),
                candidate.applicationMethod()
        );
    }
}
