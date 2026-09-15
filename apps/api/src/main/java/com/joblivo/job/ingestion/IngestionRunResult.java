package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result model capturing the outcome of an ingestion run for a single source.
 * Tracks candidate limits, received candidates, considered candidates, operational lifecycle status,
 * execution duration, and partitioned persistence outcomes.
 */
public record IngestionRunResult(
        String runId,
        JobSource source,
        IngestionRunStatus status,
        Instant startedAt,
        Instant completedAt,
        int totalCandidates,
        int createdCount,
        int updatedCount,
        int skippedCount,
        int failedCount,
        int successfulCount,
        List<CandidateFailureSummary> failureSummaries,
        int candidatesReceived,
        int candidatesConsidered
) {

    public IngestionRunResult {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (status == null) {
            status = (failedCount > 0) ? IngestionRunStatus.COMPLETED_WITH_ERRORS : IngestionRunStatus.COMPLETED;
        }
        failureSummaries = failureSummaries != null ? List.copyOf(failureSummaries) : List.of();
    }

    /**
     * Backward-compatible constructor deriving lifecycle status from failure count.
     */
    public IngestionRunResult(
            String runId,
            JobSource source,
            Instant startedAt,
            Instant completedAt,
            int totalCandidates,
            int createdCount,
            int updatedCount,
            int skippedCount,
            int failedCount,
            int successfulCount,
            List<CandidateFailureSummary> failureSummaries,
            int candidatesReceived,
            int candidatesConsidered
    ) {
        this(
                runId,
                source,
                (failedCount > 0) ? IngestionRunStatus.COMPLETED_WITH_ERRORS : IngestionRunStatus.COMPLETED,
                startedAt,
                completedAt,
                totalCandidates,
                createdCount,
                updatedCount,
                skippedCount,
                failedCount,
                successfulCount,
                failureSummaries,
                candidatesReceived,
                candidatesConsidered
        );
    }

    /**
     * Backward-compatible constructor defaulting candidatesReceived and candidatesConsidered to totalCandidates.
     */
    public IngestionRunResult(
            String runId,
            JobSource source,
            Instant startedAt,
            Instant completedAt,
            int totalCandidates,
            int createdCount,
            int updatedCount,
            int skippedCount,
            int failedCount,
            int successfulCount,
            List<CandidateFailureSummary> failureSummaries
    ) {
        this(
                runId,
                source,
                (failedCount > 0) ? IngestionRunStatus.COMPLETED_WITH_ERRORS : IngestionRunStatus.COMPLETED,
                startedAt,
                completedAt,
                totalCandidates,
                createdCount,
                updatedCount,
                skippedCount,
                failedCount,
                successfulCount,
                failureSummaries,
                totalCandidates,
                totalCandidates
        );
    }

    /**
     * Returns the execution duration of the ingestion run.
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
     * Returns the number of candidate duplicates skipped within this ingestion batch.
     * Derived deterministically from failure summaries matching the DUPLICATE category.
     */
    public int duplicateCount() {
        if (failureSummaries == null || failureSummaries.isEmpty()) {
            return 0;
        }
        return (int) failureSummaries.stream()
                .filter(s -> "DUPLICATE_IN_BATCH".equalsIgnoreCase(s.failureCategory()) || "DUPLICATE".equalsIgnoreCase(s.failureCategory()))
                .count();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String runId;
        private JobSource source;
        private IngestionRunStatus status;
        private Instant startedAt = Instant.now();
        private Instant completedAt = Instant.now();
        private int totalCandidates;
        private int createdCount;
        private int updatedCount;
        private int skippedCount;
        private int failedCount;
        private int successfulCount;
        private int candidatesReceived;
        private int candidatesConsidered;
        private final List<CandidateFailureSummary> failureSummaries = new ArrayList<>();

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder source(JobSource source) {
            this.source = source;
            return this;
        }

        public Builder status(IngestionRunStatus status) {
            this.status = status;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder totalCandidates(int totalCandidates) {
            this.totalCandidates = totalCandidates;
            return this;
        }

        public Builder createdCount(int createdCount) {
            this.createdCount = createdCount;
            return this;
        }

        public Builder updatedCount(int updatedCount) {
            this.updatedCount = updatedCount;
            return this;
        }

        public Builder skippedCount(int skippedCount) {
            this.skippedCount = skippedCount;
            return this;
        }

        public Builder failedCount(int failedCount) {
            this.failedCount = failedCount;
            return this;
        }

        public Builder successfulCount(int successfulCount) {
            this.successfulCount = successfulCount;
            return this;
        }

        public Builder candidatesReceived(int candidatesReceived) {
            this.candidatesReceived = candidatesReceived;
            return this;
        }

        public Builder candidatesConsidered(int candidatesConsidered) {
            this.candidatesConsidered = candidatesConsidered;
            return this;
        }

        public Builder addFailure(CandidateFailureSummary summary) {
            if (summary != null) {
                this.failureSummaries.add(summary);
            }
            return this;
        }

        public Builder failureSummaries(List<CandidateFailureSummary> summaries) {
            this.failureSummaries.clear();
            if (summaries != null) {
                this.failureSummaries.addAll(summaries);
            }
            return this;
        }

        public IngestionRunResult build() {
            int effectiveSuccessful = (successfulCount > 0) ? successfulCount : (createdCount + updatedCount);
            int effectiveReceived = (candidatesReceived > 0) ? candidatesReceived : totalCandidates;
            int effectiveConsidered = (candidatesConsidered > 0) ? candidatesConsidered : totalCandidates;
            int effectiveTotal = (totalCandidates > 0) ? totalCandidates : effectiveConsidered;
            IngestionRunStatus effectiveStatus = this.status;
            if (effectiveStatus == null) {
                effectiveStatus = (failedCount > 0) ? IngestionRunStatus.COMPLETED_WITH_ERRORS : IngestionRunStatus.COMPLETED;
            }

            return new IngestionRunResult(
                    runId != null ? runId : java.util.UUID.randomUUID().toString(),
                    source,
                    effectiveStatus,
                    startedAt,
                    completedAt,
                    effectiveTotal,
                    createdCount,
                    updatedCount,
                    skippedCount,
                    failedCount,
                    effectiveSuccessful,
                    Collections.unmodifiableList(new ArrayList<>(failureSummaries)),
                    effectiveReceived,
                    effectiveConsidered
            );
        }
    }
}
