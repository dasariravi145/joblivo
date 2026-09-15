package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;

import java.util.Objects;

/**
 * Immutable operational execution context passed to {@link JobSourceAdapter} invocations.
 * <p>
 * Provides controlled execution information strictly necessary for candidate discovery
 * without exposing Spring internals, database entities, credentials, tokens, sessions,
 * or user-specific data.
 * <p>
 * <b>Security & Architectural Boundaries:</b>
 * <ul>
 *   <li>Contains ONLY operational discovery parameters: correlation {@code runId}, strongly-typed {@code source},
 *       and effective {@code candidateLimit}.</li>
 *   <li>Intentionally excludes all authentication credentials, secrets, tokens, passwords, cookies, and HTTP headers.</li>
 *   <li>Intentionally excludes all user-specific context (user IDs, career profile IDs, user preferences, resume data).</li>
 *   <li>Intentionally excludes Spring application context, repositories, entity managers, and domain entities.</li>
 * </ul>
 *
 * @param runId          correlation identifier for distributed tracing and operational diagnostics; must not be blank
 * @param source         strongly-typed {@link JobSource} identity for the adapter execution; must not be null
 * @param candidateLimit effective candidate limit for batch discovery; must be positive (&gt; 0)
 */
public record JobSourceExecutionContext(
        String runId,
        JobSource source,
        int candidateLimit
) {

    public JobSourceExecutionContext {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be null or blank");
        }
        runId = runId.trim();
        Objects.requireNonNull(source, "source must not be null");
        if (candidateLimit <= 0) {
            throw new IllegalArgumentException("candidateLimit must be positive (got " + candidateLimit + ")");
        }
    }

    /**
     * Factory method creating a validated {@link JobSourceExecutionContext}.
     *
     * @param runId          correlation run identifier
     * @param source         job source identity
     * @param candidateLimit positive candidate retrieval limit
     * @return non-null immutable execution context
     */
    public static JobSourceExecutionContext of(String runId, JobSource source, int candidateLimit) {
        return new JobSourceExecutionContext(runId, source, candidateLimit);
    }
}
