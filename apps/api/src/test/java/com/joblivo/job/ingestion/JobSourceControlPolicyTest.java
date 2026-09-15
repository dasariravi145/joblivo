package com.joblivo.job.ingestion;

import com.joblivo.job.config.JobDiscoveryProperties;
import com.joblivo.job.config.JobSourceProperties;
import com.joblivo.job.model.JobSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JobSourceControlPolicy Unit Tests")
class JobSourceControlPolicyTest {

    @Mock
    private JobSourceAdapter linkedinAdapter;

    @Mock
    private JobSourceAdapter naukriAdapter;

    private JobDiscoveryProperties properties;
    private JobSourceRegistry registry;
    private JobSourceControlPolicy policy;

    @BeforeEach
    void setUp() {
        when(linkedinAdapter.getSource()).thenReturn(JobSource.LINKEDIN);
        when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);

        when(naukriAdapter.getSource()).thenReturn(JobSource.NAUKRI);
        when(naukriAdapter.source()).thenReturn(JobSource.NAUKRI);

        properties = new JobDiscoveryProperties();
        properties.setEnabled(true);
        registry = new JobSourceRegistry(List.of(linkedinAdapter, naukriAdapter), properties);
        policy = new JobSourceControlPolicy(properties, registry);
    }

    @Nested
    @DisplayName("Source Enablement Resolution")
    class EnablementTests {

        @Test
        @DisplayName("Recognizes configured enabled and disabled sources")
        void recognizesConfiguredSources() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 50),
                    "NAUKRI", new JobSourceProperties(false, 100)
            ));
            properties.validateConfiguration();

            assertThat(policy.isSourceEnabled(JobSource.LINKEDIN)).isTrue();
            assertThat(policy.isSourceDisabled(JobSource.LINKEDIN)).isFalse();

            assertThat(policy.isSourceEnabled(JobSource.NAUKRI)).isFalse();
            assertThat(policy.isSourceDisabled(JobSource.NAUKRI)).isTrue();

            assertThat(policy.isSourceEnabled(JobSource.FOUNDIT)).isFalse();
            assertThat(policy.isSourceDisabled(JobSource.FOUNDIT)).isTrue();
            assertThat(policy.isSourceEnabled(null)).isFalse();
        }

        @Test
        @DisplayName("When master toggle is false, all sources are considered disabled")
        void masterToggleTurnsOffAll() {
            properties.setEnabled(false);
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 50)
            ));
            properties.validateConfiguration();

            assertThat(policy.isMasterEnabled()).isFalse();
            assertThat(policy.isSourceEnabled(JobSource.LINKEDIN)).isFalse();
            assertThat(policy.isSourceDisabled(JobSource.LINKEDIN)).isTrue();
            assertThat(policy.getEnabledSources()).isEmpty();
            assertThat(policy.getEligibleSources()).isEmpty();
        }

        @Test
        @DisplayName("Returns deterministic unmodifiable set of enabled and disabled sources")
        void deterministicEnabledAndDisabledSets() {
            properties.setSources(Map.of(
                    "NAUKRI", new JobSourceProperties(true, 100),
                    "LINKEDIN", new JobSourceProperties(true, 50)
            ));
            properties.validateConfiguration();

            Set<JobSource> enabled = policy.getEnabledSources();
            assertThat(enabled).containsExactly(JobSource.LINKEDIN, JobSource.NAUKRI);

            Set<JobSource> disabled = policy.getDisabledSources();
            assertThat(disabled).doesNotContain(JobSource.LINKEDIN, JobSource.NAUKRI);
            assertThat(disabled).contains(JobSource.FOUNDIT, JobSource.CUTSHORT, JobSource.COMPANY_CAREERS);
        }
    }

    @Nested
    @DisplayName("Candidate Limit Resolution")
    class CandidateLimitTests {

        @Test
        @DisplayName("Returns configured maxCandidates for source, or default limit if not explicitly configured")
        void resolvesCandidateLimits() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 42),
                    "NAUKRI", new JobSourceProperties(true, 150)
            ));
            properties.validateConfiguration();

            assertThat(policy.getMaxCandidates(JobSource.LINKEDIN)).isEqualTo(42);
            assertThat(policy.getMaxCandidates(JobSource.NAUKRI)).isEqualTo(150);
            assertThat(policy.getMaxCandidates(JobSource.FOUNDIT)).isEqualTo(100);
            assertThat(policy.getMaxCandidates(null)).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Eligibility & Adapter Registration Interplay")
    class EligibilityTests {

        @Test
        @DisplayName("Source is eligible only when enabled AND adapter is registered")
        void sourceEligibleWhenEnabledAndRegistered() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 50),
                    "NAUKRI", new JobSourceProperties(false, 100),
                    "CUTSHORT", new JobSourceProperties(true, 75) // Enabled, but NO adapter registered
            ));
            properties.validateConfiguration();

            // LINKEDIN: registered + enabled -> ELIGIBLE
            assertThat(policy.isSourceEligible(JobSource.LINKEDIN)).isTrue();

            // NAUKRI: registered + disabled -> NOT ELIGIBLE
            assertThat(policy.isSourceEligible(JobSource.NAUKRI)).isFalse();

            // CUTSHORT: unregistered + enabled -> NOT ELIGIBLE
            assertThat(policy.isSourceEligible(JobSource.CUTSHORT)).isFalse();

            // COMPANY_CAREERS: unregistered + disabled -> NOT ELIGIBLE
            assertThat(policy.isSourceEligible(JobSource.COMPANY_CAREERS)).isFalse();
        }

        @Test
        @DisplayName("Correctly partitions eligible sources versus enabled sources without adapter")
        void partitionsEligibleVersusMissingAdapter() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 50),
                    "CUTSHORT", new JobSourceProperties(true, 75),
                    "FOUNDIT", new JobSourceProperties(true, 80)
            ));
            properties.validateConfiguration();

            // Only LINKEDIN has an adapter among the enabled ones
            assertThat(policy.getEligibleSources()).containsExactly(JobSource.LINKEDIN);

            // CUTSHORT and FOUNDIT are enabled but lack registered adapters
            assertThat(policy.getEnabledSourcesWithoutAdapter())
                    .containsExactly(JobSource.FOUNDIT, JobSource.CUTSHORT);

            // Total enabled sources contains all three in deterministic enum order
            assertThat(policy.getEnabledSources())
                    .containsExactly(JobSource.LINKEDIN, JobSource.FOUNDIT, JobSource.CUTSHORT);
        }

        @Test
        @DisplayName("getSourceConfiguration returns deterministic properties")
        void returnsDeterministicSourceConfiguration() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 55)
            ));
            properties.validateConfiguration();

            JobSourceProperties config = policy.getSourceConfiguration(JobSource.LINKEDIN);
            assertThat(config).isNotNull();
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getMaxCandidates()).isEqualTo(55);

            JobSourceProperties unconfigured = policy.getSourceConfiguration(JobSource.FOUNDIT);
            assertThat(unconfigured).isNotNull();
            assertThat(unconfigured.isEnabled()).isFalse();
            assertThat(unconfigured.getMaxCandidates()).isEqualTo(100);
        }
    }
}
