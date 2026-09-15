package com.joblivo.job.ingestion;

/**
 * Outcome action performed for an individual job candidate during ingestion.
 */
public enum CandidateIngestionAction {
    CREATED,
    UPDATED,
    SKIPPED,
    SKIPPED_SOURCE_DUPLICATE,
    DETECTED_CROSS_SOURCE_DUPLICATE,
    FAILED
}
