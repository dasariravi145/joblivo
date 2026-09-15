package com.joblivo.job.config;

import com.joblivo.job.exception.JobConfigurationException;
import com.joblivo.job.model.JobSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobDiscoveryProperties Validation Tests")
class JobDiscoveryConfigurationValidationTest {

    private JobDiscoveryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JobDiscoveryProperties();
        properties.setEnabled(true);
    }

    @Nested
    @DisplayName("Safe Defaults")
    class SafeDefaultsTests {

        @Test
        @DisplayName("No source configuration leaves all sources disabled by default (fail-closed)")
        void noSourceConfigurationLeavesAllDisabled() {
            properties.validateConfiguration();

            for (JobSource source : JobSource.values()) {
                assertThat(properties.isSourceEnabled(source)).isFalse();
                assertThat(properties.getMaxCandidates(source)).isEqualTo(JobDiscoveryProperties.DEFAULT_MAX_CANDIDATES);
            }
            assertThat(properties.getResolvedEnabledSources()).isEmpty();
        }

        @Test
        @DisplayName("Default maximum candidates is 100")
        void defaultMaxCandidatesIs100() {
            assertThat(properties.getDefaultMaxCandidates()).isEqualTo(100);
            assertThat(JobDiscoveryProperties.DEFAULT_MAX_CANDIDATES).isEqualTo(100);
            assertThat(JobDiscoveryProperties.MIN_CANDIDATES).isEqualTo(1);
            assertThat(JobDiscoveryProperties.MAX_CANDIDATES_UPPER_BOUND).isEqualTo(1000);
        }

        @Test
        @DisplayName("Master toggle disabled turns off all sources even if explicitly configured as enabled")
        void masterToggleDisabled() {
            properties.setEnabled(false);
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 50),
                    "NAUKRI", new JobSourceProperties(true, 75)
            ));
            properties.validateConfiguration();

            assertThat(properties.isSourceEnabled(JobSource.LINKEDIN)).isFalse();
            assertThat(properties.isSourceEnabled(JobSource.NAUKRI)).isFalse();
            assertThat(properties.getResolvedEnabledSources()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Valid Source Configuration")
    class ValidConfigurationTests {

        @Test
        @DisplayName("Configured enabled and disabled sources are correctly resolved with their candidate limits")
        void validSourceConfigurationResolved() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 50),
                    "NAUKRI", new JobSourceProperties(false, 100),
                    "COMPANY_CAREERS", new JobSourceProperties(true, 250)
            ));

            assertThatCode(() -> properties.validateConfiguration()).doesNotThrowAnyException();

            assertThat(properties.isSourceEnabled(JobSource.LINKEDIN)).isTrue();
            assertThat(properties.getMaxCandidates(JobSource.LINKEDIN)).isEqualTo(50);

            assertThat(properties.isSourceEnabled(JobSource.NAUKRI)).isFalse();
            assertThat(properties.getMaxCandidates(JobSource.NAUKRI)).isEqualTo(100);

            assertThat(properties.isSourceEnabled(JobSource.COMPANY_CAREERS)).isTrue();
            assertThat(properties.getMaxCandidates(JobSource.COMPANY_CAREERS)).isEqualTo(250);

            assertThat(properties.isSourceEnabled(JobSource.FOUNDIT)).isFalse();
            assertThat(properties.getMaxCandidates(JobSource.FOUNDIT)).isEqualTo(100);

            assertThat(properties.getResolvedEnabledSources())
                    .containsExactly(JobSource.LINKEDIN, JobSource.COMPANY_CAREERS);
        }

        @Test
        @DisplayName("Legacy enabledSources shorthand property is supported and populated with default max candidates")
        void legacyEnabledSourcesSupported() {
            properties.setEnabledSources(Set.of(JobSource.LINKEDIN, JobSource.NAUKRI));

            assertThatCode(() -> properties.validateConfiguration()).doesNotThrowAnyException();

            assertThat(properties.isSourceEnabled(JobSource.LINKEDIN)).isTrue();
            assertThat(properties.getMaxCandidates(JobSource.LINKEDIN)).isEqualTo(100);

            assertThat(properties.isSourceEnabled(JobSource.NAUKRI)).isTrue();
            assertThat(properties.getMaxCandidates(JobSource.NAUKRI)).isEqualTo(100);

            assertThat(properties.isSourceEnabled(JobSource.ATS)).isFalse();
            assertThat(properties.getResolvedEnabledSources())
                    .containsExactly(JobSource.LINKEDIN, JobSource.NAUKRI);
        }

        @Test
        @DisplayName("Explicit per-source configuration overrides legacy enabledSources")
        void explicitSourcesOverridesLegacy() {
            properties.setEnabledSources(Set.of(JobSource.LINKEDIN));
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(false, 30)
            ));

            properties.validateConfiguration();

            assertThat(properties.isSourceEnabled(JobSource.LINKEDIN)).isFalse();
            assertThat(properties.getMaxCandidates(JobSource.LINKEDIN)).isEqualTo(30);
            assertThat(properties.getResolvedEnabledSources()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Candidate Limit Safety Bounds Validation")
    class CandidateLimitValidationTests {

        @Test
        @DisplayName("Rejects zero maxCandidates")
        void rejectsZeroMaxCandidates() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 0)
            ));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("maxCandidates for source 'LINKEDIN' must be positive (got 0)");
        }

        @Test
        @DisplayName("Rejects negative maxCandidates")
        void rejectsNegativeMaxCandidates() {
            properties.setSources(Map.of(
                    "NAUKRI", new JobSourceProperties(true, -10)
            ));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("maxCandidates for source 'NAUKRI' must be positive (got -10)");
        }

        @Test
        @DisplayName("Rejects maxCandidates exceeding upper bound (1000)")
        void rejectsExcessiveMaxCandidates() {
            properties.setSources(Map.of(
                    "FOUNDIT", new JobSourceProperties(true, 1001)
            ));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("exceeds production-safe upper bound of 1000 (got 1001)");
        }

        @Test
        @DisplayName("Accepts boundary values 1 and 1000")
        void acceptsBoundaryValues() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 1),
                    "NAUKRI", new JobSourceProperties(true, 1000)
            ));

            assertThatCode(() -> properties.validateConfiguration()).doesNotThrowAnyException();
            assertThat(properties.getMaxCandidates(JobSource.LINKEDIN)).isEqualTo(1);
            assertThat(properties.getMaxCandidates(JobSource.NAUKRI)).isEqualTo(1000);
        }

        @Test
        @DisplayName("Rejects invalid defaultMaxCandidates")
        void rejectsInvalidDefaultMaxCandidates() {
            properties.setDefaultMaxCandidates(0);
            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("defaultMaxCandidates must be between 1 and 1000 (got 0)");

            properties.setDefaultMaxCandidates(1001);
            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("defaultMaxCandidates must be between 1 and 1000 (got 1001)");
        }
    }

    @Nested
    @DisplayName("Source Identity and Duplicate Validation")
    class SourceIdentityValidationTests {

        @Test
        @DisplayName("Rejects unsupported source identifier")
        void rejectsUnsupportedSource() {
            properties.setSources(Map.of(
                    "UNSUPPORTED_PORTAL", new JobSourceProperties(true, 100)
            ));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("Unsupported job source: 'UNSUPPORTED_PORTAL'");
        }

        @Test
        @DisplayName("Rejects blank source configuration key")
        void rejectsBlankSourceKey() {
            properties.setSources(Map.of(
                    "   ", new JobSourceProperties(true, 100)
            ));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("source configuration key must not be blank");
        }

        @Test
        @DisplayName("Rejects null source properties")
        void rejectsNullSourceProperties() {
            Map<String, JobSourceProperties> map = new LinkedHashMap<>();
            map.put("LINKEDIN", null);
            properties.setSources(map);

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("Configuration for source 'LINKEDIN' must not be null");
        }

        @Test
        @DisplayName("Rejects case-variant duplicate source definitions (e.g. linkedin vs LINKEDIN)")
        void rejectsDuplicateSourceDefinitions() {
            Map<String, JobSourceProperties> map = new LinkedHashMap<>();
            map.put("linkedin", new JobSourceProperties(true, 50));
            map.put("LINKEDIN", new JobSourceProperties(false, 100));
            properties.setSources(map);

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("Duplicate source definition detected for 'LINKEDIN'");
        }

        @Test
        @DisplayName("Case-insensitive source names are normalized correctly")
        void normalizesCaseInsensitiveSourceKeys() {
            properties.setSources(Map.of(
                    "linkedin", new JobSourceProperties(true, 42)
            ));

            properties.validateConfiguration();

            assertThat(properties.isSourceEnabled(JobSource.LINKEDIN)).isTrue();
            assertThat(properties.getMaxCandidates(JobSource.LINKEDIN)).isEqualTo(42);
        }
    }
}
