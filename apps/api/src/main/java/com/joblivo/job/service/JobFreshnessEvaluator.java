package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.config.JobFreshnessProperties;
import com.joblivo.job.model.JobFreshnessStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Deterministic canonical evaluator for Job Discovery record lifecycle and freshness normalization.
 * <p>
 * Interprets existing job timestamps relative to current UTC time without side-effects, database updates,
 * or network calls. Classifies catalog records into {@link JobFreshnessStatus}:
 * <ul>
 *     <li>{@link JobFreshnessStatus#EXPIRED}: expiresAt exists and now &gt;= expiresAt.</li>
 *     <li>{@link JobFreshnessStatus#STALE}: not expired, lastSeenAt exists, and lastSeenAt is older than the configured stale threshold.</li>
 *     <li>{@link JobFreshnessStatus#ACTIVE}: not expired and verified fresh (lastSeenAt within threshold, or valid non-expired postedAt).</li>
 *     <li>{@link JobFreshnessStatus#UNKNOWN}: timestamps are missing, insufficient, or contradictory.</li>
 * </ul>
 * <p>
 * <strong>Important Distinction:</strong>
 * "ACTIVE" reflects data freshness in Joblivo based on available metadata; it does not constitute a guarantee that
 * the employer is still actively hiring.
 */
@Component
public class JobFreshnessEvaluator {

    private static final JobFreshnessEvaluator DEFAULT_INSTANCE = new JobFreshnessEvaluator();

    private final Clock clock;
    private final JobFreshnessProperties properties;

    @Autowired
    public JobFreshnessEvaluator(JobFreshnessProperties properties, Clock clock) {
        this.properties = Objects.requireNonNullElseGet(properties, JobFreshnessProperties::new);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    public JobFreshnessEvaluator(JobFreshnessProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public JobFreshnessEvaluator(Clock clock) {
        this(new JobFreshnessProperties(), clock);
    }

    public JobFreshnessEvaluator() {
        this(new JobFreshnessProperties(), Clock.systemUTC());
    }

    /**
     * Evaluates the freshness status of a {@link Job} entity.
     *
     * @param job domain entity to evaluate
     * @return derived {@link JobFreshnessStatus}
     */
    public JobFreshnessStatus evaluate(Job job) {
        if (job == null) {
            return JobFreshnessStatus.UNKNOWN;
        }
        return evaluate(
                job.getPostedAt(),
                job.getExpiresAt(),
                job.getDiscoveredAt(),
                job.getLastSeenAt()
        );
    }

    /**
     * Evaluates job freshness deterministically from raw timestamp coordinates.
     *
     * @param postedAt     posting timestamp (optional)
     * @param expiresAt    expiration timestamp (optional)
     * @param discoveredAt first discovered timestamp (optional)
     * @param lastSeenAt   last observed timestamp (optional)
     * @return derived {@link JobFreshnessStatus}
     */
    public JobFreshnessStatus evaluate(Instant postedAt, Instant expiresAt, Instant discoveredAt, Instant lastSeenAt) {
        Instant now = clock.instant();

        // 1. Insufficient timestamp information
        if (lastSeenAt == null && postedAt == null && expiresAt == null && discoveredAt == null) {
            return JobFreshnessStatus.UNKNOWN;
        }

        // 2. Contradictory timestamp detection
        if (postedAt != null && expiresAt != null && expiresAt.isBefore(postedAt)) {
            return JobFreshnessStatus.UNKNOWN;
        }
        if (discoveredAt != null && lastSeenAt != null && lastSeenAt.isBefore(discoveredAt)) {
            return JobFreshnessStatus.UNKNOWN;
        }
        if (postedAt != null && postedAt.isAfter(now)) {
            return JobFreshnessStatus.UNKNOWN;
        }
        if (lastSeenAt != null && lastSeenAt.isAfter(now)) {
            return JobFreshnessStatus.UNKNOWN;
        }
        if (discoveredAt != null && discoveredAt.isAfter(now)) {
            return JobFreshnessStatus.UNKNOWN;
        }

        // 3. EXPIRED: expiresAt exists and now >= expiresAt
        // Takes precedence over STALE (e.g. stale job with past expiration is classified as EXPIRED)
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            return JobFreshnessStatus.EXPIRED;
        }

        // 4. STALE: not expired, lastSeenAt exists, and lastSeenAt is older than configured threshold
        Duration staleThreshold = properties.getStaleAfter();
        Instant staleCutoff = now.minus(staleThreshold);
        if (lastSeenAt != null && lastSeenAt.isBefore(staleCutoff)) {
            return JobFreshnessStatus.STALE;
        }

        // 5. ACTIVE: not expired and data is fresh
        // If lastSeenAt exists and is within threshold (at boundary or more recent), classify ACTIVE.
        if (lastSeenAt != null) {
            return JobFreshnessStatus.ACTIVE;
        }

        // If lastSeenAt is absent, but postedAt is valid, recent, and unexpired, classify ACTIVE.
        if (postedAt != null) {
            return JobFreshnessStatus.ACTIVE;
        }

        // 6. Insufficient evidence of when job was posted or seen fresh
        return JobFreshnessStatus.UNKNOWN;
    }

    /**
     * Static convenience evaluation using default production settings and system UTC clock.
     */
    public static JobFreshnessStatus evaluateStatic(Job job) {
        return DEFAULT_INSTANCE.evaluate(job);
    }

    /**
     * Static convenience evaluation for timestamp coordinates using default production settings.
     */
    public static JobFreshnessStatus evaluateStatic(Instant postedAt, Instant expiresAt, Instant discoveredAt, Instant lastSeenAt) {
        return DEFAULT_INSTANCE.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
    }

    public Clock getClock() {
        return clock;
    }

    public JobFreshnessProperties getProperties() {
        return properties;
    }
}
