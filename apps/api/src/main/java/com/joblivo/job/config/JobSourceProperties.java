package com.joblivo.job.config;

import java.util.Objects;

/**
 * Strongly-typed ingestion configuration controls for an individual {@link com.joblivo.job.model.JobSource}.
 * Enforces fail-closed defaults: no external source is enabled by default.
 */
public class JobSourceProperties {

    /**
     * Whether ingestion is enabled for this source.
     * Default: false (fail-closed safety; external sources must be explicitly enabled).
     */
    private boolean enabled = false;

    /**
     * Maximum candidates to process from this source per single ingestion run execution.
     * Default: 100.
     */
    private int maxCandidates = JobDiscoveryProperties.DEFAULT_MAX_CANDIDATES;

    public JobSourceProperties() {
    }

    public JobSourceProperties(boolean enabled, int maxCandidates) {
        this.enabled = enabled;
        this.maxCandidates = maxCandidates;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobSourceProperties that = (JobSourceProperties) o;
        return enabled == that.enabled && maxCandidates == that.maxCandidates;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, maxCandidates);
    }

    @Override
    public String toString() {
        return "JobSourceProperties{" +
                "enabled=" + enabled +
                ", maxCandidates=" + maxCandidates +
                '}';
    }
}
