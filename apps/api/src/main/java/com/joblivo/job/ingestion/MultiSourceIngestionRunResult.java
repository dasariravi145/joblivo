package com.joblivo.job.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable aggregated result model capturing the outcome of an ingestion run across multiple sources.
 * Tracks operational lifecycle status, total sources, duration, and partitioned candidate statistics.
 */
public record MultiSourceIngestionRunResult(
        String runId,
        IngestionRunStatus status,
        Instant startedAt,
        Instant completedAt,
        int totalSources,
        int totalCandidates,
        int totalCreated,
        int totalUpdated,
        int totalSkipped,
        int totalFailed,
        int totalSuccessful,
        List<IngestionRunResult> sourceResults,
        int totalCandidatesReceived,
        int totalCandidatesConsidered
) {

    public MultiSourceIngestionRunResult {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (status == null) {
            status = determineStatus(sourceResults, totalFailed);
        }
        sourceResults = sourceResults != null ? List.copyOf(sourceResults) : List.of();
    }

    /**
     * Backward-compatible constructor defaulting status.
     */
    public MultiSourceIngestionRunResult(
            String runId,
            Instant startedAt,
            Instant completedAt,
            int totalSources,
            int totalCandidates,
            int totalCreated,
            int totalUpdated,
            int totalSkipped,
            int totalFailed,
            int totalSuccessful,
            List<IngestionRunResult> sourceResults,
            int totalCandidatesReceived,
            int totalCandidatesConsidered
    ) {
        this(
                runId,
                determineStatus(sourceResults, totalFailed),
                startedAt,
                completedAt,
                totalSources,
                totalCandidates,
                totalCreated,
                totalUpdated,
                totalSkipped,
                totalFailed,
                totalSuccessful,
                sourceResults,
                totalCandidatesReceived,
                totalCandidatesConsidered
        );
    }

    /**
     * Backward-compatible constructor defaulting totalCandidatesReceived and totalCandidatesConsidered to totalCandidates.
     */
    public MultiSourceIngestionRunResult(
            String runId,
            Instant startedAt,
            Instant completedAt,
            int totalSources,
            int totalCandidates,
            int totalCreated,
            int totalUpdated,
            int totalSkipped,
            int totalFailed,
            int totalSuccessful,
            List<IngestionRunResult> sourceResults
    ) {
        this(
                runId,
                startedAt,
                completedAt,
                totalSources,
                totalCandidates,
                totalCreated,
                totalUpdated,
                totalSkipped,
                totalFailed,
                totalSuccessful,
                sourceResults,
                totalCandidates,
                totalCandidates
        );
    }

    /**
     * Returns the execution duration of the multi-source ingestion run.
     */
    public Duration duration() {
        return Duration.between(startedAt, completedAt);
    }

    /**
     * Returns the execution duration in milliseconds.
     */
    public long durationMs() {
        return duration().toMillis();
    }

    /**
     * Returns the aggregate count of candidate duplicates skipped across all sources in this multi-source run.
     */
    public int totalDuplicatesSkipped() {
        if (sourceResults == null || sourceResults.isEmpty()) {
            return 0;
        }
        return sourceResults.stream().mapToInt(IngestionRunResult::duplicateCount).sum();
    }

    private static IngestionRunStatus determineStatus(List<IngestionRunResult> sourceResults, int totalFailed) {
        if (totalFailed > 0) {
            return IngestionRunStatus.COMPLETED_WITH_ERRORS;
        }
        if (sourceResults != null && sourceResults.stream().anyMatch(r ->
                r.status() == IngestionRunStatus.COMPLETED_WITH_ERRORS || r.status() == IngestionRunStatus.FAILED)) {
            return IngestionRunStatus.COMPLETED_WITH_ERRORS;
        }
        return IngestionRunStatus.COMPLETED;
    }
}
