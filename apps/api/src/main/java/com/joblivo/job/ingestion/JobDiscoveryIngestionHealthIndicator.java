package com.joblivo.job.ingestion;

import com.joblivo.job.config.JobDiscoveryProperties;
import com.joblivo.job.exception.JobConfigurationException;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.service.JobIngestionOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Production-ready health and readiness indicator for the internal Job Discovery ingestion subsystem.
 * <p>
 * Evaluates configuration validity and runtime component wiring without executing external network calls,
 * starting ingestion runs, or altering persistence state.
 * <p>
 * Health states:
 * <ul>
 *   <li>{@code UP} (readiness: {@code READY}): Valid configuration, master enabled, at least one source enabled,
 *       and all enabled sources have registered adapters.</li>
 *   <li>{@code UP} (readiness: {@code NO_SOURCES_ENABLED} / {@code DISABLED}): Valid configuration, but zero sources
 *       enabled or master toggle disabled. Safe operational state that does NOT degrade the overall application.</li>
 *   <li>{@code DEGRADED} (readiness: {@code DEGRADED}): One or more configured enabled sources lack registered adapters.</li>
 *   <li>{@code DOWN} (readiness: {@code UNHEALTHY}): A required internal component is missing or configuration is
 *       structurally invalid.</li>
 * </ul>
 */
@Component("jobDiscoveryIngestion")
public class JobDiscoveryIngestionHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(JobDiscoveryIngestionHealthIndicator.class);

    public static final Status STATUS_DEGRADED = new Status("DEGRADED");

    public static final String READINESS_READY = "READY";
    public static final String READINESS_NO_SOURCES_ENABLED = "NO_SOURCES_ENABLED";
    public static final String READINESS_DISABLED = "DISABLED";
    public static final String READINESS_DEGRADED = "DEGRADED";
    public static final String READINESS_UNHEALTHY = "UNHEALTHY";

    private final JobDiscoveryProperties properties;
    private final JobSourceRegistry sourceRegistry;
    private final JobSourceControlPolicy controlPolicy;
    private final JobIngestionOrchestrator orchestrator;

    @Autowired
    public JobDiscoveryIngestionHealthIndicator(
            @Autowired(required = false) JobDiscoveryProperties properties,
            @Autowired(required = false) JobSourceRegistry sourceRegistry,
            @Autowired(required = false) JobSourceControlPolicy controlPolicy,
            @Autowired(required = false) JobIngestionOrchestrator orchestrator
    ) {
        this.properties = properties;
        this.sourceRegistry = sourceRegistry;
        this.controlPolicy = controlPolicy;
        this.orchestrator = orchestrator;
    }

    @Override
    public Health health() {
        // 1. Verify required internal ingestion components are available
        if (properties == null) {
            return buildDownHealth("JobDiscoveryProperties configuration bean is unavailable");
        }
        if (sourceRegistry == null) {
            return buildDownHealth("JobSourceRegistry component is unavailable");
        }
        if (controlPolicy == null) {
            return buildDownHealth("JobSourceControlPolicy component is unavailable");
        }
        if (orchestrator == null) {
            return buildDownHealth("JobIngestionOrchestrator component is unavailable");
        }

        // 2. Validate configuration structurally
        try {
            properties.validateConfiguration();
        } catch (JobConfigurationException ex) {
            log.warn("Job discovery ingestion configuration validation failed: {}", ex.getMessage());
            return buildDownHealth("Configuration validation error: " + ex.getMessage());
        } catch (Exception ex) {
            log.warn("Unexpected error validating job discovery configuration: {}", ex.getMessage());
            return buildDownHealth("Unexpected configuration error: " + ex.getMessage());
        }

        // 3. Inspect runtime wiring and source policy
        boolean masterEnabled = controlPolicy.isMasterEnabled();
        Set<JobSource> enabledSources = controlPolicy.getEnabledSources();
        Set<JobSource> registeredSources = sourceRegistry.getRegisteredSources();
        Set<JobSource> unavailableEnabledSources = controlPolicy.getEnabledSourcesWithoutAdapter();

        int configuredSourceCount = properties.getSources() != null && !properties.getSources().isEmpty()
                ? properties.getSources().size()
                : properties.getEnabledSources().size();
        int enabledSourceCount = enabledSources.size();
        int registeredAdapterCount = registeredSources.size();

        // 4. Evaluate operational health states
        if (!masterEnabled) {
            return Health.up()
                    .withDetail("readiness", READINESS_DISABLED)
                    .withDetail("masterEnabled", false)
                    .withDetail("configuredSourceCount", configuredSourceCount)
                    .withDetail("enabledSourceCount", 0)
                    .withDetail("registeredAdapterCount", registeredAdapterCount)
                    .withDetail("unavailableSources", Collections.emptyList())
                    .withDetail("message", "Job discovery ingestion master toggle is disabled")
                    .build();
        }

        if (enabledSources.isEmpty()) {
            return Health.up()
                    .withDetail("readiness", READINESS_NO_SOURCES_ENABLED)
                    .withDetail("masterEnabled", true)
                    .withDetail("configuredSourceCount", configuredSourceCount)
                    .withDetail("enabledSourceCount", 0)
                    .withDetail("registeredAdapterCount", registeredAdapterCount)
                    .withDetail("unavailableSources", Collections.emptyList())
                    .withDetail("message", "No job discovery ingestion sources are currently enabled")
                    .build();
        }

        if (!unavailableEnabledSources.isEmpty()) {
            List<String> unavailableNames = unavailableEnabledSources.stream()
                    .map(JobSource::name)
                    .sorted()
                    .toList();

            return Health.status(STATUS_DEGRADED)
                    .withDetail("readiness", READINESS_DEGRADED)
                    .withDetail("masterEnabled", true)
                    .withDetail("configuredSourceCount", configuredSourceCount)
                    .withDetail("enabledSourceCount", enabledSourceCount)
                    .withDetail("registeredAdapterCount", registeredAdapterCount)
                    .withDetail("unavailableSources", unavailableNames)
                    .withDetail("message", "One or more enabled job sources lack registered adapters")
                    .build();
        }

        // Subsystem is fully ready
        return Health.up()
                .withDetail("readiness", READINESS_READY)
                .withDetail("masterEnabled", true)
                .withDetail("configuredSourceCount", configuredSourceCount)
                .withDetail("enabledSourceCount", enabledSourceCount)
                .withDetail("registeredAdapterCount", registeredAdapterCount)
                .withDetail("unavailableSources", Collections.emptyList())
                .withDetail("message", "Job discovery ingestion subsystem is ready")
                .build();
    }

    private Health buildDownHealth(String errorMessage) {
        return Health.down()
                .withDetail("readiness", READINESS_UNHEALTHY)
                .withDetail("masterEnabled", properties != null && properties.isEnabled())
                .withDetail("error", errorMessage)
                .build();
    }
}
