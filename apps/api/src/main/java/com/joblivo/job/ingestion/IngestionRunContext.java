package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable operational context tracking the lifecycle state and provenance of an ingestion execution.
 * Scoped strictly to an individual execution; thread-safe and free from global or static mutable state.
 */
public record IngestionRunContext(
        String runId,
        IngestionRunStatus status,
        Instant startedAt,
        Set<JobSource> sources
) {

    public IngestionRunContext {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        sources = sources != null ? Collections.unmodifiableSet(new TreeSet<>(sources)) : Collections.emptySet();
    }

    /**
     * Factory creating a new run context in {@link IngestionRunStatus#STARTED} state for a single source.
     */
    public static IngestionRunContext start(String runId, JobSource source) {
        Set<JobSource> sourceSet = (source != null) ? Set.of(source) : Set.of();
        return new IngestionRunContext(runId, IngestionRunStatus.STARTED, Instant.now(), sourceSet);
    }

    /**
     * Factory creating a new run context in {@link IngestionRunStatus#STARTED} state for multiple sources.
     */
    public static IngestionRunContext start(String runId, Set<JobSource> sources) {
        return new IngestionRunContext(runId, IngestionRunStatus.STARTED, Instant.now(), sources);
    }

    /**
     * Advances lifecycle to the given target state, strictly enforcing valid forward transitions.
     *
     * @param nextStatus the next status to transition to
     * @return a new immutable context with updated status
     * @throws IllegalStateException if transition is invalid
     */
    public IngestionRunContext transitionTo(IngestionRunStatus nextStatus) {
        status.validateTransition(nextStatus);
        return new IngestionRunContext(runId, nextStatus, startedAt, sources);
    }

    /**
     * Advances lifecycle from {@link IngestionRunStatus#STARTED} to {@link IngestionRunStatus#RUNNING}.
     */
    public IngestionRunContext toRunning() {
        return transitionTo(IngestionRunStatus.RUNNING);
    }

    /**
     * Advances lifecycle to {@link IngestionRunStatus#COMPLETED}.
     */
    public IngestionRunContext toCompleted() {
        return transitionTo(IngestionRunStatus.COMPLETED);
    }

    /**
     * Advances lifecycle to {@link IngestionRunStatus#COMPLETED_WITH_ERRORS}.
     */
    public IngestionRunContext toCompletedWithErrors() {
        return transitionTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
    }

    /**
     * Advances lifecycle to {@link IngestionRunStatus#FAILED}.
     */
    public IngestionRunContext toFailed() {
        return transitionTo(IngestionRunStatus.FAILED);
    }
}
