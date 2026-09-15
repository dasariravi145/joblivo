package com.joblivo.job.model;

/**
 * Normalized work mode categorization for job postings.
 * If the source does not explicitly provide sufficient information, {@link #UNKNOWN} must be used.
 */
public enum JobWorkMode {
    REMOTE,
    HYBRID,
    ONSITE,
    UNKNOWN
}
