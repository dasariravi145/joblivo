package com.joblivo.job.ingestion;

import java.util.Objects;

/**
 * Immutable verification result of the 7-point ingestion execution policy.
 * <p>
 * Evaluated authoritatively before any adapter invocation:
 * <ol>
 *   <li>Source is known (non-null recognized domain identity)</li>
 *   <li>Source has a registered adapter</li>
 *   <li>Adapter declared source matches requested execution source</li>
 *   <li>Source is implemented (authoritative adapter registered in registry)</li>
 *   <li>Source is enabled in configuration (and master toggle enabled)</li>
 *   <li>Execution context is non-null and valid</li>
 *   <li>Candidate limit is positive and within safety bounds</li>
 * </ol>
 */
public record JobSourceExecutionVerification(
        boolean valid,
        String rejectionCategory,
        String rejectionReason,
        JobSourceCoverage coverage
) {

    public JobSourceExecutionVerification {
        Objects.requireNonNull(coverage, "coverage must not be null");
    }

    /**
     * Returns true if all 7 execution safety conditions passed.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Factory for successful policy verification.
     */
    public static JobSourceExecutionVerification valid(JobSourceCoverage coverage) {
        Objects.requireNonNull(coverage, "coverage must not be null");
        return new JobSourceExecutionVerification(true, null, null, coverage);
    }

    /**
     * Factory for rejected policy verification.
     */
    public static JobSourceExecutionVerification rejected(
            String rejectionCategory,
            String rejectionReason,
            JobSourceCoverage coverage
    ) {
        Objects.requireNonNull(coverage, "coverage must not be null");
        return new JobSourceExecutionVerification(
                false,
                rejectionCategory != null ? rejectionCategory : "CONFIGURATION",
                rejectionReason != null ? rejectionReason : coverage.notPermittedReason(),
                coverage
        );
    }
}
