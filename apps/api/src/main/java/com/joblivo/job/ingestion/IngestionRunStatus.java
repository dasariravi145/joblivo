package com.joblivo.job.ingestion;

/**
 * Controlled operational lifecycle states for Job Discovery ingestion runs.
 * Represents the execution lifecycle of the orchestration run, not individual job records.
 */
public enum IngestionRunStatus {

    /**
     * Ingestion run has been initiated and execution context initialized.
     */
    STARTED,

    /**
     * Ingestion run is actively fetching candidates, normalizing, or persisting batches.
     */
    RUNNING,

    /**
     * Ingestion run completed successfully with zero errors or failures.
     */
    COMPLETED,

    /**
     * Ingestion run completed, but encountered one or more candidate-level or non-fatal source-level errors.
     */
    COMPLETED_WITH_ERRORS,

    /**
     * Ingestion run encountered a fatal orchestration or infrastructure error preventing meaningful completion.
     */
    FAILED;

    /**
     * Checks if this status is terminal (cannot transition to any other status).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == FAILED;
    }

    /**
     * Checks if a transition from this status to the given next status is valid.
     *
     * @param next the target status
     * @return true if valid forward transition, false otherwise
     */
    public boolean canTransitionTo(IngestionRunStatus next) {
        if (next == null || isTerminal()) {
            return false;
        }
        return switch (this) {
            case STARTED -> next == RUNNING || next == FAILED;
            case RUNNING -> next == COMPLETED || next == COMPLETED_WITH_ERRORS || next == FAILED;
            default -> false;
        };
    }

    /**
     * Validates that transitioning to the given target status is allowed.
     *
     * @param next the target status
     * @throws IllegalStateException if the transition is invalid or moves backward
     */
    public void validateTransition(IngestionRunStatus next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException(String.format(
                    "Invalid ingestion run lifecycle transition from '%s' to '%s'", this, next
            ));
        }
    }
}
