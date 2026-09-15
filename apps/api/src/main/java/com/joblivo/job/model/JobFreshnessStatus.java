package com.joblivo.job.model;

/**
 * Strongly-typed lifecycle and freshness classification for Job Discovery catalog records.
 * Represents a deterministic, system-level interpretation of existing job timestamps.
 * <p>
 * <strong>Important Distinction:</strong>
 * This status reflects the chronological freshness and validity of the job data available to Joblivo.
 * {@link #ACTIVE} indicates that available timestamp metadata does not indicate expiration or staleness;
 * it must <em>not</em> be presented as a guarantee that the employer is actively hiring or accepting applications.
 */
public enum JobFreshnessStatus {

    /**
     * The job data is current and has not expired or exceeded the stale threshold based on available timestamps.
     * Does not guarantee that the employer is still accepting applications.
     */
    ACTIVE,

    /**
     * The job's expiration timestamp (expiresAt) has been reached or passed relative to current time.
     */
    EXPIRED,

    /**
     * The job has not explicitly expired, but its lastSeenAt timestamp is older than the configured stale threshold.
     */
    STALE,

    /**
     * Timestamp information is insufficient, missing, or contradictory to safely evaluate job freshness.
     */
    UNKNOWN
}
