package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;

import java.util.Objects;
import java.util.Optional;

/**
 * Canonical outcome of the single internal source-resolution path.
 * <p>
 * Given a {@link JobSource}, this record encapsulates:
 * <ul>
 *   <li>The resolved {@link JobSourceAdapter}, if one exists in the authoritative registry</li>
 *   <li>The {@link JobSourceCoverage} model describing implementation, configuration, and coverage states</li>
 *   <li>Whether ingestion execution is permitted under current policy and wiring</li>
 *   <li>Diagnostic rejection category and reason when execution is prohibited</li>
 * </ul>
 */
public record JobSourceResolution(
        JobSource source,
        JobSourceAdapter adapter,
        JobSourceCoverage coverage,
        boolean executionAllowed,
        String rejectionCategory,
        String rejectionReason
) {

    public JobSourceResolution {
        Objects.requireNonNull(coverage, "coverage must not be null");
    }

    /**
     * Returns true if execution is permitted for this resolved source.
     */
    public boolean isExecutionAllowed() {
        return executionAllowed;
    }

    /**
     * Returns true if an adapter was resolved.
     */
    public boolean hasAdapter() {
        return adapter != null;
    }

    /**
     * Returns an Optional wrapping the resolved adapter.
     */
    public Optional<JobSourceAdapter> getAdapter() {
        return Optional.ofNullable(adapter);
    }

    /**
     * Factory for a successfully resolved source that is permitted to execute.
     */
    public static JobSourceResolution permitted(JobSource source, JobSourceAdapter adapter, JobSourceCoverage coverage) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(adapter, "adapter must not be null");
        Objects.requireNonNull(coverage, "coverage must not be null");
        return new JobSourceResolution(source, adapter, coverage, true, null, null);
    }

    /**
     * Factory for a resolved source that is rejected by policy or missing implementation.
     */
    public static JobSourceResolution rejected(
            JobSource source,
            JobSourceAdapter adapter,
            JobSourceCoverage coverage,
            String rejectionCategory,
            String rejectionReason
    ) {
        Objects.requireNonNull(coverage, "coverage must not be null");
        return new JobSourceResolution(
                source,
                adapter,
                coverage,
                false,
                rejectionCategory != null ? rejectionCategory : "CONFIGURATION",
                rejectionReason != null ? rejectionReason : coverage.notPermittedReason()
        );
    }
}
