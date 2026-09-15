package com.joblivo.job.config;

import com.joblivo.job.exception.JobConfigurationException;
import com.joblivo.job.model.JobSource;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Type-safe configuration properties for Job Discovery ingestion sources.
 * Namespaced under {@code joblivo.job-discovery}.
 */
@ConfigurationProperties(prefix = "joblivo.job-discovery")
public class JobDiscoveryProperties {

    public static final int DEFAULT_MAX_CANDIDATES = 100;
    public static final int MIN_CANDIDATES = 1;
    public static final int MAX_CANDIDATES_UPPER_BOUND = 1000;

    /**
     * Master toggle for job discovery ingestion orchestration. Default: true.
     */
    private boolean enabled = true;

    /**
     * Default maximum candidates per source run if not explicitly overridden at source level.
     * Default: 100.
     */
    private int defaultMaxCandidates = DEFAULT_MAX_CANDIDATES;

    /**
     * Set of enabled job sources (legacy shorthand property).
     * Defaults to empty for fail-closed safety; no external sources are enabled automatically.
     */
    private Set<JobSource> enabledSources = EnumSet.noneOf(JobSource.class);

    /**
     * Strongly-typed source configurations keyed by source name.
     */
    private Map<String, JobSourceProperties> sources = new LinkedHashMap<>();

    /**
     * Validated and resolved source properties indexed by {@link JobSource}.
     */
    private final Map<JobSource, JobSourceProperties> resolvedSources = new EnumMap<>(JobSource.class);

    @PostConstruct
    public void init() {
        validateConfiguration();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultMaxCandidates() {
        return defaultMaxCandidates;
    }

    public void setDefaultMaxCandidates(int defaultMaxCandidates) {
        this.defaultMaxCandidates = defaultMaxCandidates;
    }

    public Set<JobSource> getEnabledSources() {
        return Collections.unmodifiableSet(enabledSources);
    }

    public void setEnabledSources(Set<JobSource> enabledSources) {
        this.enabledSources = enabledSources != null && !enabledSources.isEmpty()
                ? EnumSet.copyOf(enabledSources)
                : EnumSet.noneOf(JobSource.class);
    }

    public Map<String, JobSourceProperties> getSources() {
        return sources;
    }

    public void setSources(Map<String, JobSourceProperties> sources) {
        this.sources = sources != null ? new LinkedHashMap<>(sources) : new LinkedHashMap<>();
    }

    /**
     * Programmatic configuration helper for setting source properties by enum.
     */
    public void setSource(JobSource source, boolean enabled, int maxCandidates) {
        if (source != null) {
            JobSourceProperties props = new JobSourceProperties(enabled, maxCandidates);
            this.sources.put(source.name(), props);
            this.resolvedSources.put(source, props);
        }
    }

    /**
     * Programmatic configuration helper for setting source properties.
     */
    public void setSourceProperties(JobSource source, JobSourceProperties properties) {
        if (source != null) {
            if (properties != null) {
                this.sources.put(source.name(), properties);
                this.resolvedSources.put(source, properties);
            } else {
                this.sources.remove(source.name());
                this.resolvedSources.remove(source);
            }
        }
    }

    /**
     * Returns the validated and resolved source properties map.
     */
    public Map<JobSource, JobSourceProperties> getResolvedSources() {
        return Collections.unmodifiableMap(resolvedSources);
    }

    /**
     * Retrieves the resolved {@link JobSourceProperties} for a source.
     */
    public JobSourceProperties getSourceProperties(JobSource source) {
        if (source == null) {
            return null;
        }
        JobSourceProperties props = resolvedSources.get(source);
        if (props != null) {
            return props;
        }
        boolean isLegacyEnabled = enabledSources.contains(source);
        return new JobSourceProperties(isLegacyEnabled, defaultMaxCandidates);
    }

    /**
     * Checks if a given job source is enabled.
     *
     * @param source the source to check
     * @return true if master toggle is enabled and the source is configured as enabled
     */
    public boolean isSourceEnabled(JobSource source) {
        if (!enabled || source == null) {
            return false;
        }
        JobSourceProperties props = resolvedSources.get(source);
        if (props != null) {
            return props.isEnabled();
        }
        if (sources == null || sources.isEmpty()) {
            return enabledSources.contains(source);
        }
        return false;
    }

    /**
     * Resolves the maximum candidate limit for a given source.
     *
     * @param source the source to check
     * @return configured max candidates or default limit
     */
    public int getMaxCandidates(JobSource source) {
        if (source != null && resolvedSources.containsKey(source)) {
            return resolvedSources.get(source).getMaxCandidates();
        }
        return defaultMaxCandidates;
    }

    /**
     * Returns an unmodifiable set of all sources configured as enabled in deterministic alphabetical order.
     */
    public Set<JobSource> getResolvedEnabledSources() {
        if (!enabled) {
            return Collections.emptySet();
        }
        Set<JobSource> result = new TreeSet<>();
        for (Map.Entry<JobSource, JobSourceProperties> entry : resolvedSources.entrySet()) {
            if (entry.getValue().isEnabled()) {
                result.add(entry.getKey());
            }
        }
        if (sources == null || sources.isEmpty()) {
            for (JobSource legacy : enabledSources) {
                if (isSourceEnabled(legacy)) {
                    result.add(legacy);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Validates configuration at startup.
     * Enforces:
     * - defaultMaxCandidates within safe bounds [MIN_CANDIDATES, MAX_CANDIDATES_UPPER_BOUND]
     * - all configured source keys match supported {@link JobSource} enum values
     * - no duplicate source definitions
     * - source maxCandidates is positive and within safe production bounds
     *
     * @throws JobConfigurationException if configuration is malformed or exceeds safety bounds
     */
    public void validateConfiguration() {
        if (defaultMaxCandidates < MIN_CANDIDATES || defaultMaxCandidates > MAX_CANDIDATES_UPPER_BOUND) {
            throw new JobConfigurationException(String.format(
                    "defaultMaxCandidates must be between %d and %d (got %d)",
                    MIN_CANDIDATES, MAX_CANDIDATES_UPPER_BOUND, defaultMaxCandidates
            ));
        }

        resolvedSources.clear();
        Set<JobSource> seen = new HashSet<>();

        if (sources != null && !sources.isEmpty()) {
            for (Map.Entry<String, JobSourceProperties> entry : sources.entrySet()) {
                String rawKey = entry.getKey();
                if (rawKey == null || rawKey.isBlank()) {
                    throw new JobConfigurationException("Job discovery source configuration key must not be blank");
                }

                JobSource source;
                try {
                    source = JobSource.valueOf(rawKey.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new JobConfigurationException(String.format(
                            "Unsupported job source: '%s'. Supported sources are: %s",
                            rawKey.trim(), Arrays.toString(JobSource.values())
                    ));
                }

                if (!seen.add(source)) {
                    throw new JobConfigurationException("Duplicate source definition detected for '" + source + "'");
                }

                JobSourceProperties props = entry.getValue();
                if (props == null) {
                    throw new JobConfigurationException("Configuration for source '" + source + "' must not be null");
                }

                if (props.getMaxCandidates() < MIN_CANDIDATES) {
                    throw new JobConfigurationException(String.format(
                            "maxCandidates for source '%s' must be positive (got %d)",
                            source, props.getMaxCandidates()
                    ));
                }

                if (props.getMaxCandidates() > MAX_CANDIDATES_UPPER_BOUND) {
                    throw new JobConfigurationException(String.format(
                            "maxCandidates for source '%s' exceeds production-safe upper bound of %d (got %d)",
                            source, MAX_CANDIDATES_UPPER_BOUND, props.getMaxCandidates()
                    ));
                }

                resolvedSources.put(source, props);
            }
        } else if (enabledSources != null) {
            // Apply legacy enabledSources only if sources map was not configured
            for (JobSource legacySource : enabledSources) {
                if (legacySource != null) {
                    resolvedSources.put(legacySource, new JobSourceProperties(true, defaultMaxCandidates));
                }
            }
        }
    }
}
