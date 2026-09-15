package com.joblivo.job.ingestion;

import com.joblivo.job.duplicate.JobDuplicateClassification;
import com.joblivo.job.duplicate.JobDuplicateResult;
import com.joblivo.job.service.JobResponse;

import java.util.Objects;

/**
 * Result of ingesting an individual candidate into the job catalog, specifying the entity projection,
 * action taken, and optional deterministic duplicate detection evidence.
 *
 * @param job             the persisted job response, or null if action is FAILED
 * @param action          the outcome action taken
 * @param duplicateResult optional duplicate detection evidence evaluated against stored jobs
 * @param message         optional diagnostic explanation or skip/failure reason
 */
public record JobCandidateIngestionResult(
        JobResponse job,
        CandidateIngestionAction action,
        JobDuplicateResult duplicateResult,
        String message
) {

    public JobCandidateIngestionResult {
        Objects.requireNonNull(action, "action must not be null");
        if (action != CandidateIngestionAction.FAILED) {
            Objects.requireNonNull(job, "job must not be null for action " + action);
        }
    }

    /**
     * Backward-compatible 2-argument constructor.
     */
    public JobCandidateIngestionResult(JobResponse job, CandidateIngestionAction action) {
        this(job, action, null, null);
    }

    /**
     * Checks whether this ingestion result matched a deterministic duplicate.
     *
     * @return true if duplicate evidence was found, false otherwise
     */
    public boolean isDuplicate() {
        return duplicateResult != null && duplicateResult.isDuplicate();
    }

    /**
     * Checks whether this candidate matched an exact source identity duplicate.
     *
     * @return true if exact source duplicate, false otherwise
     */
    public boolean isSourceDuplicate() {
        return duplicateResult != null && duplicateResult.classification() == JobDuplicateClassification.EXACT_SOURCE_DUPLICATE;
    }

    /**
     * Checks whether this candidate matched a cross-source duplicate (via URL or content fingerprint).
     *
     * @return true if cross-source duplicate, false otherwise
     */
    public boolean isCrossSourceDuplicate() {
        return duplicateResult != null && (
                duplicateResult.classification() == JobDuplicateClassification.EXACT_URL_DUPLICATE
                        || duplicateResult.classification() == JobDuplicateClassification.EXACT_CONTENT_DUPLICATE
        );
    }

    public static JobCandidateIngestionResult created(JobResponse job) {
        return new JobCandidateIngestionResult(job, CandidateIngestionAction.CREATED, null, null);
    }

    public static JobCandidateIngestionResult created(JobResponse job, JobDuplicateResult duplicateResult) {
        return new JobCandidateIngestionResult(job, CandidateIngestionAction.CREATED, duplicateResult, null);
    }

    public static JobCandidateIngestionResult updated(JobResponse job) {
        return new JobCandidateIngestionResult(job, CandidateIngestionAction.UPDATED, null, null);
    }

    public static JobCandidateIngestionResult updated(JobResponse job, JobDuplicateResult duplicateResult) {
        return new JobCandidateIngestionResult(job, CandidateIngestionAction.UPDATED, duplicateResult, null);
    }

    public static JobCandidateIngestionResult skipped(JobResponse job) {
        return new JobCandidateIngestionResult(job, CandidateIngestionAction.SKIPPED, null, null);
    }

    public static JobCandidateIngestionResult skipped(JobResponse job, String reason) {
        return new JobCandidateIngestionResult(job, CandidateIngestionAction.SKIPPED, null, reason);
    }

    public static JobCandidateIngestionResult skippedSourceDuplicate(JobResponse job, JobDuplicateResult duplicateResult) {
        return new JobCandidateIngestionResult(job, CandidateIngestionAction.SKIPPED_SOURCE_DUPLICATE, duplicateResult, "Exact source identity already exists");
    }

    public static JobCandidateIngestionResult detectedCrossSourceDuplicate(JobResponse job, JobDuplicateResult duplicateResult) {
        return new JobCandidateIngestionResult(job, CandidateIngestionAction.DETECTED_CROSS_SOURCE_DUPLICATE, duplicateResult, null);
    }

    public static JobCandidateIngestionResult failed(String reason) {
        return new JobCandidateIngestionResult(null, CandidateIngestionAction.FAILED, null, reason);
    }
}

