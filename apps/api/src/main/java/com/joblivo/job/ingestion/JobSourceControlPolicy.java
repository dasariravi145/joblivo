package com.joblivo.job.ingestion;

import com.joblivo.job.config.JobDiscoveryProperties;
import com.joblivo.job.config.JobSourceProperties;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobSourceMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Control-plane policy and resolution component for Job Discovery ingestion sources.
 * <p>
 * Evaluates source enablement, per-source candidate limits, and ingestion eligibility
 * without mixing persistence, normalization, or adapter execution responsibilities.
 * <p>
 * Separation of concerns:
 * <ul>
 *   <li>{@link JobSourceRegistry}: Knows which adapters are registered in the application.</li>
 *   <li>{@link JobDiscoveryProperties}: Knows source configuration and configured limits.</li>
 *   <li>{@link JobSourceControlPolicy}: Evaluates deterministic source control policy and eligibility.</li>
 *   <li>{@link com.joblivo.job.service.JobIngestionOrchestrator}: Coordinates execution using registry and policy.</li>
 * </ul>
 */
@Component
public class JobSourceControlPolicy {

    private final JobDiscoveryProperties properties;
    private final JobSourceRegistry sourceRegistry;

    @Autowired
    public JobSourceControlPolicy(JobDiscoveryProperties properties, JobSourceRegistry sourceRegistry) {
        this.properties = Objects.requireNonNull(properties, "JobDiscoveryProperties must not be null");
        this.sourceRegistry = Objects.requireNonNull(sourceRegistry, "JobSourceRegistry must not be null");
    }

    /**
     * Checks if the master job discovery capability is enabled.
     */
    public boolean isMasterEnabled() {
        return properties.isEnabled();
    }

    /**
     * Checks whether a specific job source is enabled in configuration.
     * Evaluates to false if master toggle is off or source is not enabled.
     *
     * @param source the job source to check
     * @return true if master toggle is on and the source is enabled in configuration
     */
    public boolean isSourceEnabled(JobSource source) {
        if (!properties.isEnabled() || source == null) {
            return false;
        }
        return properties.isSourceEnabled(source);
    }

    /**
     * Checks whether a specific job source is disabled.
     *
     * @param source the job source to check
     * @return true if master toggle is off or source is not enabled
     */
    public boolean isSourceDisabled(JobSource source) {
        return !isSourceEnabled(source);
    }

    /**
     * Returns the maximum candidate limit for a given source per ingestion run.
     *
     * @param source the job source
     * @return configured max candidates or default safe limit
     */
    public int getMaxCandidates(JobSource source) {
        return properties.getMaxCandidates(source);
    }

    /**
     * Determines whether a source is fully eligible for ingestion execution.
     * A source is eligible if and only if:
     * <ol>
     *   <li>Master job discovery is enabled</li>
     *   <li>The source is enabled in configuration</li>
     *   <li>A corresponding {@link JobSourceAdapter} is registered in {@link JobSourceRegistry}</li>
     * </ol>
     *
     * @param source the job source
     * @return true if the source is configured enabled AND has a registered adapter
     */
    public boolean isSourceEligible(JobSource source) {
        return resolveSource(source).isExecutionAllowed();
    }

    /**
     * Returns the canonical, immutable {@link JobSourceCoverage} model for a specific source.
     * Evaluates implementation status, configuration state, and execution permission.
     *
     * @param source the job source
     * @return immutable JobSourceCoverage
     */
    public JobSourceCoverage getSourceCoverage(JobSource source) {
        if (source == null) {
            return JobSourceCoverage.unknown("JobSource must not be null");
        }

        boolean isRegistered = sourceRegistry.isSourceRegistered(source);
        boolean isConfigEnabled = properties.isSourceEnabled(source);
        boolean isMaster = properties.isEnabled();

        if (!isRegistered) {
            String reason = isConfigEnabled
                    ? "Source '" + source + "' is not implemented; cannot execute even though configuration requests enabled=true"
                    : "Source '" + source + "' is not implemented; no JobSourceAdapter is registered";
            return JobSourceCoverage.notImplemented(source, isConfigEnabled, reason);
        }

        if (!isMaster) {
            return JobSourceCoverage.configuredButDisabled(source, "Master job discovery is disabled");
        }

        if (!isConfigEnabled) {
            return JobSourceCoverage.configuredButDisabled(source, "Source '" + source + "' is disabled in configuration");
        }

        return JobSourceCoverage.supported(source);
    }

    /**
     * Returns canonical coverage models for all conceptual {@link JobSource} origins in deterministic order.
     *
     * @return unmodifiable list of JobSourceCoverage
     */
    public List<JobSourceCoverage> getAllSourceCoverage() {
        List<JobSourceCoverage> list = new ArrayList<>(JobSource.values().length);
        for (JobSource source : JobSource.values()) {
            list.add(getSourceCoverage(source));
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Canonical single internal source-resolution path.
     * <p>
     * Resolves the adapter, coverage model, implementation status, configuration status,
     * and whether execution is permitted.
     *
     * @param source the job source to resolve
     * @return canonical JobSourceResolution
     */
    public JobSourceResolution resolveSource(JobSource source) {
        JobSourceCoverage coverage = getSourceCoverage(source);
        if (source == null) {
            return JobSourceResolution.rejected(
                    null,
                    null,
                    coverage,
                    "INVALID_SOURCE",
                    "JobSource must not be null"
            );
        }

        Optional<JobSourceAdapter> adapter = sourceRegistry.getAdapter(source);
        if (adapter.isEmpty()) {
            return JobSourceResolution.rejected(
                    source,
                    null,
                    coverage,
                    "ADAPTER_NOT_FOUND",
                    coverage.notPermittedReason()
            );
        }

        if (!coverage.isExecutionPermitted()) {
            return JobSourceResolution.rejected(
                    source,
                    adapter.get(),
                    coverage,
                    "SOURCE_DISABLED",
                    coverage.notPermittedReason()
            );
        }

        return JobSourceResolution.permitted(source, adapter.get(), coverage);
    }

    /**
     * Evaluates the canonical 7-point ingestion execution policy before adapter execution:
     * <ol>
     *   <li>Source is known (non-null)</li>
     *   <li>Source has a valid adapter</li>
     *   <li>Adapter declared source matches requested source</li>
     *   <li>Source is implemented (registered in JobSourceRegistry)</li>
     *   <li>Source is enabled (master enabled and source configured enabled)</li>
     *   <li>Execution context is non-null and valid</li>
     *   <li>Candidate limit is positive and within safe bounds</li>
     * </ol>
     *
     * @param source  the requested job source
     * @param adapter the resolved adapter (may be null if unresolvable)
     * @param context the execution context
     * @return immutable JobSourceExecutionVerification
     */
    public JobSourceExecutionVerification verifyExecution(
            JobSource source,
            JobSourceAdapter adapter,
            JobSourceExecutionContext context
    ) {
        JobSourceCoverage coverage = getSourceCoverage(source);

        // 1. Source is known
        if (source == null) {
            return JobSourceExecutionVerification.rejected(
                    "INVALID_SOURCE",
                    "JobSource must not be null",
                    coverage
            );
        }

        // 2. Source has a valid adapter
        if (adapter == null) {
            return JobSourceExecutionVerification.rejected(
                    "ADAPTER_NOT_FOUND",
                    "No adapter registered for source: " + source,
                    coverage
            );
        }

        // 3. Adapter source matches requested source
        if (adapter.source() != source) {
            return JobSourceExecutionVerification.rejected(
                    "SOURCE_MISMATCH",
                    "Adapter declared source '" + adapter.source() + "' does not match execution context source '" + source + "'",
                    coverage
            );
        }

        // 4. Source is implemented
        if (!coverage.isImplemented()) {
            return JobSourceExecutionVerification.rejected(
                    "ADAPTER_NOT_FOUND",
                    coverage.notPermittedReason(),
                    coverage
            );
        }

        // 5. Source is enabled
        if (!coverage.isExecutionPermitted()) {
            return JobSourceExecutionVerification.rejected(
                    "SOURCE_DISABLED",
                    coverage.notPermittedReason(),
                    coverage
            );
        }

        // 6. Execution context is valid
        if (context == null) {
            return JobSourceExecutionVerification.rejected(
                    "INVALID_EXECUTION_CONTEXT",
                    "JobSourceExecutionContext must not be null",
                    coverage
            );
        }
        if (context.source() != source) {
            return JobSourceExecutionVerification.rejected(
                    "SOURCE_MISMATCH",
                    "Execution context source '" + context.source() + "' does not match requested source '" + source + "'",
                    coverage
            );
        }
        if (context.runId() == null || context.runId().isBlank()) {
            return JobSourceExecutionVerification.rejected(
                    "INVALID_EXECUTION_CONTEXT",
                    "Execution context runId must not be null or blank",
                    coverage
            );
        }

        // 7. Candidate limit is valid
        if (context.candidateLimit() < JobDiscoveryProperties.MIN_CANDIDATES
                || context.candidateLimit() > JobDiscoveryProperties.MAX_CANDIDATES_UPPER_BOUND) {
            return JobSourceExecutionVerification.rejected(
                    "INVALID_EXECUTION_CONTEXT",
                    String.format(
                            "Candidate limit %d is invalid; must be between %d and %d",
                            context.candidateLimit(),
                            JobDiscoveryProperties.MIN_CANDIDATES,
                            JobDiscoveryProperties.MAX_CANDIDATES_UPPER_BOUND
                    ),
                    coverage
            );
        }

        return JobSourceExecutionVerification.valid(coverage);
    }

    /**
     * Convenience method evaluating execution policy using the registry's adapter.
     */
    public JobSourceExecutionVerification verifyExecution(JobSource source, JobSourceExecutionContext context) {
        JobSourceAdapter adapter = (source != null) ? sourceRegistry.getAdapter(source).orElse(null) : null;
        return verifyExecution(source, adapter, context);
    }

    /**
     * Returns an unmodifiable set of all sources configured as enabled, in deterministic alphabetical order.
     */
    public Set<JobSource> getEnabledSources() {
        return properties.getResolvedEnabledSources();
    }

    /**
     * Returns an unmodifiable set of all sources that are disabled, in deterministic alphabetical order.
     */
    public Set<JobSource> getDisabledSources() {
        Set<JobSource> disabled = new TreeSet<>();
        for (JobSource source : JobSource.values()) {
            if (isSourceDisabled(source)) {
                disabled.add(source);
            }
        }
        return Collections.unmodifiableSet(disabled);
    }

    /**
     * Returns an unmodifiable set of configured enabled sources that also have a registered adapter,
     * ordered deterministically.
     */
    public Set<JobSource> getEligibleSources() {
        Set<JobSource> eligible = new TreeSet<>();
        for (JobSource source : getEnabledSources()) {
            if (sourceRegistry.isSourceRegistered(source)) {
                eligible.add(source);
            }
        }
        return Collections.unmodifiableSet(eligible);
    }

    /**
     * Returns an unmodifiable set of sources configured as enabled that lack a registered adapter,
     * ordered deterministically.
     */
    public Set<JobSource> getEnabledSourcesWithoutAdapter() {
        Set<JobSource> missing = new TreeSet<>();
        for (JobSource source : getEnabledSources()) {
            if (!sourceRegistry.isSourceRegistered(source)) {
                missing.add(source);
            }
        }
        return Collections.unmodifiableSet(missing);
    }

    /**
     * Returns deterministic resolved configuration properties for a source.
     *
     * @param source the job source
     * @return source properties containing enabled status and maxCandidates
     */
    public JobSourceProperties getSourceConfiguration(JobSource source) {
        return properties.getSourceProperties(source);
    }

    /**
     * Returns canonical descriptive source metadata for a specific {@link JobSource}
     * evaluated against this control policy.
     *
     * @param source the job source
     * @return JobSourceMetadata, or null if source is null
     */
    public JobSourceMetadata getSourceMetadata(JobSource source) {
        if (source == null) {
            return null;
        }
        return JobSourceMetadata.of(source, isSourceEnabled(source));
    }
}
