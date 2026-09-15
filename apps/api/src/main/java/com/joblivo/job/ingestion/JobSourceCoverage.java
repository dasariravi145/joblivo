package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;

import java.util.Objects;

/**
 * Canonical, immutable domain record representing the coverage and execution policy
 * status for a job discovery source.
 * <p>
 * Evaluates whether an adapter is implemented, what configuration specifies,
 * whether execution is permitted, and the exact reason when execution is prohibited.
 * This structure is strictly internal, read-only, and never persisted in the database.
 */
public record JobSourceCoverage(
        JobSource source,
        JobSourceImplementationStatus implementationStatus,
        boolean configuredEnabled,
        JobSourceCoverageStatus coverageStatus,
        boolean executionPermitted,
        String notPermittedReason
) {

    public JobSourceCoverage {
        Objects.requireNonNull(implementationStatus, "implementationStatus must not be null");
        Objects.requireNonNull(coverageStatus, "coverageStatus must not be null");
    }

    /**
     * Returns true if this source is fully supported and permitted to execute.
     */
    public boolean isSupported() {
        return coverageStatus == JobSourceCoverageStatus.SUPPORTED;
    }

    /**
     * Returns true if an authoritative adapter is implemented for this source.
     */
    public boolean isImplemented() {
        return implementationStatus.isImplemented();
    }

    /**
     * Returns true if this source is marked enabled in configuration.
     */
    public boolean isConfiguredEnabled() {
        return configuredEnabled;
    }

    /**
     * Returns true if execution is permitted under current policy and wiring.
     */
    public boolean isExecutionPermitted() {
        return executionPermitted;
    }

    /**
     * Factory for an implemented, configured-enabled source that is permitted to execute.
     */
    public static JobSourceCoverage supported(JobSource source) {
        Objects.requireNonNull(source, "source must not be null for supported coverage");
        return new JobSourceCoverage(
                source,
                JobSourceImplementationStatus.IMPLEMENTED,
                true,
                JobSourceCoverageStatus.SUPPORTED,
                true,
                null
        );
    }

    /**
     * Factory for an implemented source that is currently disabled in configuration or by master toggle.
     */
    public static JobSourceCoverage configuredButDisabled(JobSource source, String reason) {
        Objects.requireNonNull(source, "source must not be null");
        return new JobSourceCoverage(
                source,
                JobSourceImplementationStatus.IMPLEMENTED,
                false,
                JobSourceCoverageStatus.CONFIGURED_BUT_DISABLED,
                false,
                reason != null && !reason.isBlank() ? reason : "Source '" + source + "' is disabled in configuration"
        );
    }

    /**
     * Factory for a source that is known conceptually, but has no adapter implemented.
     * Enforces the invariant: UNIMPLEMENTED SOURCE == NEVER EXECUTABLE.
     */
    public static JobSourceCoverage notImplemented(JobSource source, boolean configuredEnabled, String reason) {
        Objects.requireNonNull(source, "source must not be null");
        String effectiveReason = reason != null && !reason.isBlank()
                ? reason
                : (configuredEnabled
                ? "Source '" + source + "' is not implemented; cannot execute even though configuration requests enabled=true"
                : "Source '" + source + "' is not implemented; no JobSourceAdapter is registered");

        return new JobSourceCoverage(
                source,
                JobSourceImplementationStatus.NOT_IMPLEMENTED,
                configuredEnabled,
                JobSourceCoverageStatus.NOT_IMPLEMENTED,
                false,
                effectiveReason
        );
    }

    /**
     * Factory for a null or unrecognized source.
     */
    public static JobSourceCoverage unknown(String reason) {
        return new JobSourceCoverage(
                null,
                JobSourceImplementationStatus.NOT_IMPLEMENTED,
                false,
                JobSourceCoverageStatus.UNKNOWN,
                false,
                reason != null && !reason.isBlank() ? reason : "Job source is unknown or null"
        );
    }
}
