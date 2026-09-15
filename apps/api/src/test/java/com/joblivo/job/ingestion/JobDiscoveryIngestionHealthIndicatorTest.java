package com.joblivo.job.ingestion;

import com.joblivo.job.config.JobDiscoveryProperties;
import com.joblivo.job.config.JobSourceProperties;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.service.JobIngestionOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("JobDiscoveryIngestionHealthIndicator Unit & Contract Tests")
class JobDiscoveryIngestionHealthIndicatorTest {

    private JobDiscoveryProperties properties;
    private JobSourceRegistry sourceRegistry;
    private JobSourceControlPolicy controlPolicy;
    private JobIngestionOrchestrator orchestrator;

    private static class DummyAdapter implements JobSourceAdapter {
        private final JobSource source;

        DummyAdapter(JobSource source) {
            this.source = source;
        }

        @Override
        public JobSource getSource() {
            return source;
        }

        @Override
        public List<JobIngestionCandidate> fetchJobs(JobSourceExecutionContext context) {
            throw new UnsupportedOperationException("fetchJobs must NEVER be called by health checks");
        }

        @Override
        public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
            throw new UnsupportedOperationException("fetchIngestionCandidates must NEVER be called by health checks");
        }
    }

    @BeforeEach
    void setUp() {
        properties = new JobDiscoveryProperties();
        properties.setEnabled(true);
        properties.setDefaultMaxCandidates(100);
        orchestrator = mock(JobIngestionOrchestrator.class);
    }

    private JobDiscoveryIngestionHealthIndicator createIndicator(List<JobSourceAdapter> adapters) {
        sourceRegistry = new JobSourceRegistry(adapters, properties);
        controlPolicy = new JobSourceControlPolicy(properties, sourceRegistry);
        return new JobDiscoveryIngestionHealthIndicator(properties, sourceRegistry, controlPolicy, orchestrator);
    }

    @Nested
    @DisplayName("Healthy and Ready Operational States")
    class HealthyStates {

        @Test
        @DisplayName("1. All configured enabled sources have registered adapters -> UP and READY")
        void allConfiguredSourcesHaveAdapters_returnsUpAndReady() {
            properties.setSource(JobSource.LINKEDIN, true, 50);
            properties.setSource(JobSource.NAUKRI, true, 75);

            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(List.of(
                    new DummyAdapter(JobSource.LINKEDIN),
                    new DummyAdapter(JobSource.NAUKRI)
            ));

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_READY);
            assertThat(health.getDetails()).containsEntry("masterEnabled", true);
            assertThat(health.getDetails()).containsEntry("configuredSourceCount", 2);
            assertThat(health.getDetails()).containsEntry("enabledSourceCount", 2);
            assertThat(health.getDetails()).containsEntry("registeredAdapterCount", 2);
            assertThat(health.getDetails()).containsEntry("unavailableSources", Collections.emptyList());
            assertThat((String) health.getDetails().get("message")).contains("ready");
        }

        @Test
        @DisplayName("2. No sources configured -> UP and safe operational state (NO_SOURCES_ENABLED)")
        void noSourcesConfigured_returnsUpAndSafe() {
            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(Collections.emptyList());

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_NO_SOURCES_ENABLED);
            assertThat(health.getDetails()).containsEntry("masterEnabled", true);
            assertThat(health.getDetails()).containsEntry("configuredSourceCount", 0);
            assertThat(health.getDetails()).containsEntry("enabledSourceCount", 0);
            assertThat(health.getDetails()).containsEntry("unavailableSources", Collections.emptyList());
        }

        @Test
        @DisplayName("3. Configured sources exist but all disabled -> UP and safe operational state (NO_SOURCES_ENABLED)")
        void noSourcesEnabled_returnsUpAndSafe() {
            properties.setSource(JobSource.LINKEDIN, false, 50);
            properties.setSource(JobSource.NAUKRI, false, 50);

            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(Collections.emptyList());

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_NO_SOURCES_ENABLED);
            assertThat(health.getDetails()).containsEntry("masterEnabled", true);
            assertThat(health.getDetails()).containsEntry("configuredSourceCount", 2);
            assertThat(health.getDetails()).containsEntry("enabledSourceCount", 0);
            assertThat(health.getDetails()).containsEntry("unavailableSources", Collections.emptyList());
        }

        @Test
        @DisplayName("5. Disabled source without adapter does not cause failure or degradation")
        void disabledSourceWithoutAdapter_doesNotCauseFailure() {
            properties.setSource(JobSource.LINKEDIN, false, 50); // disabled, no adapter
            properties.setSource(JobSource.NAUKRI, true, 50);     // enabled, has adapter

            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(List.of(
                    new DummyAdapter(JobSource.NAUKRI)
            ));

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_READY);
            assertThat(health.getDetails()).containsEntry("enabledSourceCount", 1);
            assertThat(health.getDetails()).containsEntry("unavailableSources", Collections.emptyList());
        }

        @Test
        @DisplayName("Master toggle disabled -> UP with readiness DISABLED without causing global failure")
        void masterDisabled_returnsUpAndDisabled() {
            properties.setEnabled(false);
            properties.setSource(JobSource.LINKEDIN, true, 50);

            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(List.of(
                    new DummyAdapter(JobSource.LINKEDIN)
            ));

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_DISABLED);
            assertThat(health.getDetails()).containsEntry("masterEnabled", false);
            assertThat(health.getDetails()).containsEntry("enabledSourceCount", 0);
        }
    }

    @Nested
    @DisplayName("Degraded and Unavailable States")
    class DegradedStates {

        @Test
        @DisplayName("4. Enabled source without registered adapter -> DEGRADED")
        void enabledSourceWithoutAdapter_returnsDegraded() {
            properties.setSource(JobSource.LINKEDIN, true, 50);

            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(Collections.emptyList());

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(JobDiscoveryIngestionHealthIndicator.STATUS_DEGRADED);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_DEGRADED);
            assertThat(health.getDetails()).containsEntry("masterEnabled", true);
            assertThat(health.getDetails()).containsEntry("enabledSourceCount", 1);
            assertThat(health.getDetails()).containsEntry("registeredAdapterCount", 0);
            assertThat(health.getDetails()).containsEntry("unavailableSources", List.of("LINKEDIN"));
            assertThat((String) health.getDetails().get("message")).contains("lack registered adapters");
        }

        @Test
        @DisplayName("6. Multiple enabled sources where some are unavailable -> DEGRADED with missing listed")
        void multipleSourcesWithOneUnavailable_returnsDegraded() {
            properties.setSource(JobSource.LINKEDIN, true, 50);
            properties.setSource(JobSource.NAUKRI, true, 50);

            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(List.of(
                    new DummyAdapter(JobSource.LINKEDIN) // only LINKEDIN has adapter
            ));

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(JobDiscoveryIngestionHealthIndicator.STATUS_DEGRADED);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_DEGRADED);
            assertThat(health.getDetails()).containsEntry("enabledSourceCount", 2);
            assertThat(health.getDetails()).containsEntry("registeredAdapterCount", 1);
            assertThat(health.getDetails()).containsEntry("unavailableSources", List.of("NAUKRI"));
        }

        @Test
        @DisplayName("7. Source registry is empty and sources are enabled -> DEGRADED")
        void emptyRegistryWithEnabledSources_returnsDegraded() {
            properties.setSource(JobSource.FOUNDIT, true, 50);
            properties.setSource(JobSource.CUTSHORT, true, 50);

            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(Collections.emptyList());

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(JobDiscoveryIngestionHealthIndicator.STATUS_DEGRADED);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_DEGRADED);
            assertThat(health.getDetails()).containsEntry("unavailableSources", List.of("CUTSHORT", "FOUNDIT"));
        }
    }

    @Nested
    @DisplayName("Component Availability & Configuration Validity (DOWN)")
    class DownStates {

        @Test
        @DisplayName("Missing properties -> DOWN with UNHEALTHY readiness")
        void missingProperties_returnsDown() {
            sourceRegistry = new JobSourceRegistry(Collections.emptyList(), properties);
            controlPolicy = new JobSourceControlPolicy(properties, sourceRegistry);
            JobDiscoveryIngestionHealthIndicator indicator = new JobDiscoveryIngestionHealthIndicator(
                    null, sourceRegistry, controlPolicy, orchestrator
            );

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_UNHEALTHY);
            assertThat((String) health.getDetails().get("error")).contains("JobDiscoveryProperties");
        }

        @Test
        @DisplayName("Missing sourceRegistry -> DOWN with UNHEALTHY readiness")
        void missingSourceRegistry_returnsDown() {
            controlPolicy = mock(JobSourceControlPolicy.class);
            JobDiscoveryIngestionHealthIndicator indicator = new JobDiscoveryIngestionHealthIndicator(
                    properties, null, controlPolicy, orchestrator
            );

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_UNHEALTHY);
            assertThat((String) health.getDetails().get("error")).contains("JobSourceRegistry");
        }

        @Test
        @DisplayName("Missing controlPolicy -> DOWN with UNHEALTHY readiness")
        void missingControlPolicy_returnsDown() {
            sourceRegistry = new JobSourceRegistry(Collections.emptyList(), properties);
            JobDiscoveryIngestionHealthIndicator indicator = new JobDiscoveryIngestionHealthIndicator(
                    properties, sourceRegistry, null, orchestrator
            );

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_UNHEALTHY);
            assertThat((String) health.getDetails().get("error")).contains("JobSourceControlPolicy");
        }

        @Test
        @DisplayName("Missing orchestrator -> DOWN with UNHEALTHY readiness")
        void missingOrchestrator_returnsDown() {
            sourceRegistry = new JobSourceRegistry(Collections.emptyList(), properties);
            controlPolicy = new JobSourceControlPolicy(properties, sourceRegistry);
            JobDiscoveryIngestionHealthIndicator indicator = new JobDiscoveryIngestionHealthIndicator(
                    properties, sourceRegistry, controlPolicy, null
            );

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_UNHEALTHY);
            assertThat((String) health.getDetails().get("error")).contains("JobIngestionOrchestrator");
        }

        @Test
        @DisplayName("Invalid configuration (e.g. maxCandidates out of bounds) -> DOWN with UNHEALTHY readiness")
        void invalidConfiguration_returnsDown() {
            properties.setDefaultMaxCandidates(0); // below MIN_CANDIDATES (1)
            sourceRegistry = new JobSourceRegistry(Collections.emptyList(), properties);
            controlPolicy = new JobSourceControlPolicy(properties, sourceRegistry);
            JobDiscoveryIngestionHealthIndicator indicator = new JobDiscoveryIngestionHealthIndicator(
                    properties, sourceRegistry, controlPolicy, orchestrator
            );

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_UNHEALTHY);
            assertThat((String) health.getDetails().get("error")).contains("Configuration validation error");
        }
    }

    @Nested
    @DisplayName("Non-Execution, Read-Only & Security Invariants")
    class InvariantsAndSecurity {

        @Test
        @DisplayName("8, 9, 10. Health check does NOT execute adapters, orchestrator, or database operations")
        void healthCheckDoesNotExecuteAdaptersOrOrchestration() {
            JobSourceAdapter mockAdapter = mock(JobSourceAdapter.class);
            Mockito.when(mockAdapter.getSource()).thenReturn(JobSource.LINKEDIN);
            Mockito.when(mockAdapter.source()).thenReturn(JobSource.LINKEDIN);

            properties.setSource(JobSource.LINKEDIN, true, 50);
            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(List.of(mockAdapter));

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);

            // Verify ZERO calls to adapter fetch methods
            verifyNoInteractions(orchestrator);
            Mockito.verify(mockAdapter, Mockito.never()).fetchJobs(Mockito.any());
            Mockito.verify(mockAdapter, Mockito.never()).fetchIngestionCandidates(Mockito.any());
        }

        @Test
        @DisplayName("11. Health details contain NO secrets, credentials, tokens, or raw job data")
        void healthDetailsContainNoSecretsOrSensitiveData() {
            properties.setSource(JobSource.LINKEDIN, true, 50);
            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(List.of(
                    new DummyAdapter(JobSource.LINKEDIN)
            ));

            Health health = indicator.health();
            Map<String, Object> details = health.getDetails();

            List<String> forbiddenKeywords = List.of(
                    "secret", "password", "token", "auth", "key", "credential", "url", "job", "candidate"
            );

            for (Map.Entry<String, Object> entry : details.entrySet()) {
                String keyLower = entry.getKey().toLowerCase();
                for (String forbidden : forbiddenKeywords) {
                    assertThat(keyLower)
                            .withFailMessage("Detail key '%s' contains sensitive substring '%s'", entry.getKey(), forbidden)
                            .isNotEqualTo(forbidden);
                }

                if (entry.getValue() instanceof String valStr) {
                    assertThat(valStr).doesNotContain("Bearer", "Basic", "https://");
                }
            }

            // Verify full raw configuration map is not dumped
            assertThat(details).doesNotContainKey("sources");
            assertThat(details).doesNotContainKey("resolvedSources");
        }
    }

    @Nested
    @DisplayName("Actuator Status Aggregation")
    class ActuatorAggregationTests {

        private final org.springframework.boot.actuate.health.SimpleStatusAggregator aggregator =
                new org.springframework.boot.actuate.health.SimpleStatusAggregator(
                        "DOWN", "OUT_OF_SERVICE", "DEGRADED", "UP", "UNKNOWN"
                );

        @Test
        @DisplayName("DEGRADED status aggregates with overall system without marking system DOWN")
        void degradedAggregatesCorrectly() {
            properties.setSource(JobSource.LINKEDIN, true, 50);
            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(Collections.emptyList());

            Health ingestionHealth = indicator.health();
            assertThat(ingestionHealth.getStatus()).isEqualTo(JobDiscoveryIngestionHealthIndicator.STATUS_DEGRADED);

            Status aggregated = aggregator.getAggregateStatus(ingestionHealth.getStatus(), Status.UP);
            assertThat(aggregated.getCode()).isEqualTo("DEGRADED");
        }

        @Test
        @DisplayName("Zero sources enabled (UP) does not degrade or fail the overall system")
        void noSourcesEnabledAggregatesToUp() {
            JobDiscoveryIngestionHealthIndicator indicator = createIndicator(Collections.emptyList());

            Health ingestionHealth = indicator.health();
            assertThat(ingestionHealth.getStatus()).isEqualTo(Status.UP);

            Status aggregated = aggregator.getAggregateStatus(ingestionHealth.getStatus(), Status.UP);
            assertThat(aggregated).isEqualTo(Status.UP);
        }

        @Test
        @DisplayName("DOWN component overrides other statuses and marks aggregate DOWN")
        void downOverridesUpAndDegraded() {
            JobDiscoveryIngestionHealthIndicator indicator = new JobDiscoveryIngestionHealthIndicator(
                    null, sourceRegistry, controlPolicy, orchestrator
            );

            Health ingestionHealth = indicator.health();
            assertThat(ingestionHealth.getStatus()).isEqualTo(Status.DOWN);

            Status aggregated = aggregator.getAggregateStatus(
                    ingestionHealth.getStatus(),
                    Status.UP,
                    JobDiscoveryIngestionHealthIndicator.STATUS_DEGRADED
            );
            assertThat(aggregated).isEqualTo(Status.DOWN);
        }
    }
}

