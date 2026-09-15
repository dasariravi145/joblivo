package com.joblivo.job.ingestion;

import com.joblivo.job.config.JobDiscoveryProperties;
import com.joblivo.job.exception.DuplicateJobSourceAdapterException;
import com.joblivo.job.model.JobSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobSourceRegistry Unit Tests")
class JobSourceRegistryTest {

    private static class TestSourceAdapter implements JobSourceAdapter {
        private final JobSource source;

        TestSourceAdapter(JobSource source) {
            this.source = source;
        }

        @Override
        public JobSource getSource() {
            return source;
        }

        @Override
        public List<JobIngestionCandidate> fetchJobs(JobSourceExecutionContext context) {
            return List.of();
        }
    }

    private static class AnotherTestSourceAdapter implements JobSourceAdapter {
        private final JobSource source;

        AnotherTestSourceAdapter(JobSource source) {
            this.source = source;
        }

        @Override
        public JobSource getSource() {
            return source;
        }

        @Override
        public List<JobIngestionCandidate> fetchJobs(JobSourceExecutionContext context) {
            return List.of();
        }
    }

    @Nested
    @DisplayName("Adapter Discovery and Registration")
    class RegistrationTests {

        @Test
        @DisplayName("Discovers and registers valid adapters")
        void registersValidAdapters() {
            JobDiscoveryProperties properties = new JobDiscoveryProperties();
            properties.setEnabledSources(Set.of(JobSource.LINKEDIN, JobSource.NAUKRI));

            JobSourceAdapter linkedinAdapter = new TestSourceAdapter(JobSource.LINKEDIN);
            JobSourceAdapter naukriAdapter = new TestSourceAdapter(JobSource.NAUKRI);

            JobSourceRegistry registry = new JobSourceRegistry(
                    List.of(linkedinAdapter, naukriAdapter),
                    properties
            );

            assertThat(registry.isSourceRegistered(JobSource.LINKEDIN)).isTrue();
            assertThat(registry.isSourceRegistered(JobSource.NAUKRI)).isTrue();
            assertThat(registry.isSourceRegistered(JobSource.COMPANY_CAREERS)).isFalse();

            assertThat(registry.getAdapter(JobSource.LINKEDIN)).contains(linkedinAdapter);
            assertThat(registry.getAdapter(JobSource.NAUKRI)).contains(naukriAdapter);
            assertThat(registry.getAdapter(JobSource.COMPANY_CAREERS)).isEmpty();
            assertThat(registry.getAdapter(null)).isEmpty();
        }

        @Test
        @DisplayName("Rejects duplicate adapter registrations for the same JobSource")
        void rejectsDuplicateAdaptersForSameSource() {
            JobDiscoveryProperties properties = new JobDiscoveryProperties();
            JobSourceAdapter adapter1 = new TestSourceAdapter(JobSource.LINKEDIN);
            JobSourceAdapter adapter2 = new AnotherTestSourceAdapter(JobSource.LINKEDIN);

            assertThatThrownBy(() -> new JobSourceRegistry(List.of(adapter1, adapter2), properties))
                    .isInstanceOf(DuplicateJobSourceAdapterException.class)
                    .hasMessageContaining("Duplicate JobSourceAdapter registration for source 'LINKEDIN'");
        }

        @Test
        @DisplayName("Handles null or empty adapter list safely")
        void handlesNullOrEmptyAdapterList() {
            JobDiscoveryProperties properties = new JobDiscoveryProperties();
            JobSourceRegistry emptyRegistry1 = new JobSourceRegistry(null, properties);
            assertThat(emptyRegistry1.getRegisteredSources()).isEmpty();
            assertThat(emptyRegistry1.getEnabledSources()).isEmpty();

            JobSourceRegistry emptyRegistry2 = new JobSourceRegistry(Collections.emptyList(), properties);
            assertThat(emptyRegistry2.getRegisteredSources()).isEmpty();
            assertThat(emptyRegistry2.getEnabledSources()).isEmpty();
        }

        @Test
        @DisplayName("Null properties throws NullPointerException")
        void nullPropertiesThrows() {
            assertThatThrownBy(() -> new JobSourceRegistry(List.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("JobDiscoveryProperties must not be null");
        }
    }

    @Nested
    @DisplayName("Source Enablement Configuration Checks")
    class EnablementTests {

        @Test
        @DisplayName("Registered source is only enabled when configured in JobDiscoveryProperties")
        void sourceEnabledWhenConfigured() {
            JobDiscoveryProperties properties = new JobDiscoveryProperties();
            properties.setEnabledSources(Set.of(JobSource.LINKEDIN));

            JobSourceAdapter linkedinAdapter = new TestSourceAdapter(JobSource.LINKEDIN);
            JobSourceAdapter naukriAdapter = new TestSourceAdapter(JobSource.NAUKRI);

            JobSourceRegistry registry = new JobSourceRegistry(
                    List.of(linkedinAdapter, naukriAdapter),
                    properties
            );

            assertThat(registry.isSourceEnabled(JobSource.LINKEDIN)).isTrue();
            assertThat(registry.isSourceEnabled(JobSource.NAUKRI)).isFalse();
            assertThat(registry.isSourceEnabled(JobSource.COMPANY_CAREERS)).isFalse();

            Set<JobSource> enabledSources = registry.getEnabledSources();
            assertThat(enabledSources).containsExactly(JobSource.LINKEDIN);
        }

        @Test
        @DisplayName("Master toggle disabled turns off all sources regardless of configured set")
        void masterToggleDisabled() {
            JobDiscoveryProperties properties = new JobDiscoveryProperties();
            properties.setEnabled(false);
            properties.setEnabledSources(Set.of(JobSource.LINKEDIN, JobSource.NAUKRI));

            JobSourceAdapter linkedinAdapter = new TestSourceAdapter(JobSource.LINKEDIN);
            JobSourceRegistry registry = new JobSourceRegistry(List.of(linkedinAdapter), properties);

            assertThat(registry.isSourceEnabled(JobSource.LINKEDIN)).isFalse();
            assertThat(registry.getEnabledSources()).isEmpty();
        }

        @Test
        @DisplayName("getRegisteredSources returns unmodifiable deterministic set")
        void registeredSourcesDeterministicAndUnmodifiable() {
            JobDiscoveryProperties properties = new JobDiscoveryProperties();
            JobSourceAdapter naukriAdapter = new TestSourceAdapter(JobSource.NAUKRI);
            JobSourceAdapter linkedinAdapter = new TestSourceAdapter(JobSource.LINKEDIN);

            JobSourceRegistry registry = new JobSourceRegistry(
                    List.of(naukriAdapter, linkedinAdapter),
                    properties
            );

            Set<JobSource> registered = registry.getRegisteredSources();
            // Deterministic alphabetical sort: LINKEDIN before NAUKRI
            assertThat(registered).containsExactly(JobSource.LINKEDIN, JobSource.NAUKRI);

            assertThatThrownBy(() -> registered.add(JobSource.ATS))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
