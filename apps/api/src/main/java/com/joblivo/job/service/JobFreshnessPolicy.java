package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.config.JobFreshnessProperties;
import com.joblivo.job.model.JobFreshnessStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Canonical, source-neutral policy component responsible for interpreting {@link JobFreshnessStatus}
 * across the Job Discovery read and query layer.
 * <p>
 * <strong>Core Operational Invariants:</strong>
 * <ul>
 *     <li><strong>Read-Only &amp; Side-Effect Free:</strong> Freshness evaluation never mutates stored job timestamps
 *         ({@code postedAt}, {@code expiresAt}, {@code discoveredAt}, {@code lastSeenAt}), never modifies database records,
 *         and never treats user searches or page views as source ingestion activity.</li>
 *     <li><strong>No Automatic Deletion / Archival:</strong> Stale, expired, or unknown jobs are never deleted or archived
 *         from persistent storage. They remain stored for history, analytics, deduplication, and market intelligence.</li>
 *     <li><strong>Discoverability:</strong> The job search read layer ({@code GET /api/v1/jobs}) does NOT silently drop
 *         or suppress stale, expired, or unknown jobs. It returns all matching jobs with their factual, derived
 *         {@link JobFreshnessStatus} transparently exposed.</li>
 *     <li><strong>Factual Interpretation:</strong> {@link JobFreshnessStatus#ACTIVE} signifies that available timestamps
 *         do not indicate expiration or staleness; it does NOT constitute a guarantee that the vacancy is currently hiring.
 *         Similarly, {@link JobFreshnessStatus#STALE} signifies that source data has not been seen recently, not that
 *         the job is definitively closed.</li>
 * </ul>
 */
@Component
public class JobFreshnessPolicy {

    private final JobFreshnessEvaluator evaluator;

    @Autowired
    public JobFreshnessPolicy(JobFreshnessEvaluator evaluator) {
        this.evaluator = Objects.requireNonNullElseGet(evaluator, JobFreshnessEvaluator::new);
    }

    public JobFreshnessPolicy(JobFreshnessProperties properties, Clock clock) {
        this(new JobFreshnessEvaluator(properties, clock));
    }

    public JobFreshnessPolicy(Clock clock) {
        this(new JobFreshnessEvaluator(clock));
    }

    public JobFreshnessPolicy() {
        this(new JobFreshnessEvaluator());
    }

    /**
     * Evaluates the factual freshness status of a {@link Job} entity.
     *
     * @param job domain entity to evaluate
     * @return derived {@link JobFreshnessStatus}
     */
    public JobFreshnessStatus evaluate(Job job) {
        return evaluator.evaluate(job);
    }

    /**
     * Evaluates the factual freshness status of raw timestamp coordinates.
     *
     * @param postedAt     original posting timestamp (optional)
     * @param expiresAt    expiration timestamp (optional)
     * @param discoveredAt first discovery timestamp (optional)
     * @param lastSeenAt   last observed timestamp (optional)
     * @return derived {@link JobFreshnessStatus}
     */
    public JobFreshnessStatus evaluate(Instant postedAt, Instant expiresAt, Instant discoveredAt, Instant lastSeenAt) {
        return evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
    }

    /**
     * Determines whether the given freshness status indicates the record is currently fresh in Joblivo.
     *
     * @param status freshness status
     * @return {@code true} if status is {@link JobFreshnessStatus#ACTIVE}, {@code false} otherwise
     */
    public boolean isCurrentlyFresh(JobFreshnessStatus status) {
        return status == JobFreshnessStatus.ACTIVE;
    }

    /**
     * Determines whether the given job is currently fresh in Joblivo based on its timestamps.
     *
     * @param job domain entity to inspect
     * @return {@code true} if evaluated as {@link JobFreshnessStatus#ACTIVE}, {@code false} otherwise
     */
    public boolean isCurrentlyFresh(Job job) {
        return isCurrentlyFresh(evaluate(job));
    }

    /**
     * Determines whether the given freshness status indicates the job is potentially expired.
     *
     * @param status freshness status
     * @return {@code true} if status is {@link JobFreshnessStatus#EXPIRED}, {@code false} otherwise
     */
    public boolean isPotentiallyExpired(JobFreshnessStatus status) {
        return status == JobFreshnessStatus.EXPIRED;
    }

    /**
     * Determines whether the given job is potentially expired based on its timestamps.
     *
     * @param job domain entity to inspect
     * @return {@code true} if evaluated as {@link JobFreshnessStatus#EXPIRED}, {@code false} otherwise
     */
    public boolean isPotentiallyExpired(Job job) {
        return isPotentiallyExpired(evaluate(job));
    }

    /**
     * Determines whether the given freshness status indicates the job is stale.
     *
     * @param status freshness status
     * @return {@code true} if status is {@link JobFreshnessStatus#STALE}, {@code false} otherwise
     */
    public boolean isStale(JobFreshnessStatus status) {
        return status == JobFreshnessStatus.STALE;
    }

    /**
     * Determines whether the given job is stale based on its timestamps.
     *
     * @param job domain entity to inspect
     * @return {@code true} if evaluated as {@link JobFreshnessStatus#STALE}, {@code false} otherwise
     */
    public boolean isStale(Job job) {
        return isStale(evaluate(job));
    }

    /**
     * Determines whether the given freshness status is unknown due to missing or contradictory timestamps.
     *
     * @param status freshness status
     * @return {@code true} if status is {@link JobFreshnessStatus#UNKNOWN}, {@code false} otherwise
     */
    public boolean isUnknown(JobFreshnessStatus status) {
        return status == JobFreshnessStatus.UNKNOWN;
    }

    /**
     * Determines whether the given job's freshness is unknown based on its timestamps.
     *
     * @param job domain entity to inspect
     * @return {@code true} if evaluated as {@link JobFreshnessStatus#UNKNOWN}, {@code false} otherwise
     */
    public boolean isUnknown(Job job) {
        return isUnknown(evaluate(job));
    }

    /**
     * Determines whether a job record should remain discoverable in search queries.
     * <p>
     * Under Joblivo's core policy, all catalog records (ACTIVE, STALE, EXPIRED, and UNKNOWN) remain discoverable
     * by default so that historical records, market telemetry, and deduplication integrity are preserved.
     *
     * @param status freshness status
     * @return {@code true} if non-null, ensuring no status is silently dropped from search
     */
    public boolean shouldRemainDiscoverable(JobFreshnessStatus status) {
        return status != null;
    }

    /**
     * Determines whether a job record should remain discoverable in search queries.
     *
     * @param job domain entity
     * @return {@code true} if job is non-null, ensuring stored jobs remain discoverable
     */
    public boolean shouldRemainDiscoverable(Job job) {
        return job != null && shouldRemainDiscoverable(evaluate(job));
    }

    /**
     * Determines whether a job record must be retained in persistent storage.
     * <p>
     * Stale, expired, and unknown records are never automatically deleted, archived, or modified.
     * They are permanently retained for deduplication, history, and analytics.
     *
     * @param status freshness status
     * @return always {@code true}
     */
    public boolean shouldRetainRecord(JobFreshnessStatus status) {
        return true;
    }

    /**
     * Determines whether a job record must be retained in persistent storage.
     *
     * @param job domain entity
     * @return always {@code true}
     */
    public boolean shouldRetainRecord(Job job) {
        return true;
    }

    /**
     * Returns the underlying canonical freshness evaluator.
     *
     * @return canonical {@link JobFreshnessEvaluator}
     */
    public JobFreshnessEvaluator getEvaluator() {
        return evaluator;
    }
}
