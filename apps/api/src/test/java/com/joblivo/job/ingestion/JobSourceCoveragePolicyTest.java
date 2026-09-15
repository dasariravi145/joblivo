package com.joblivo.job.ingestion;

import com.joblivo.job.config.JobDiscoveryProperties;
import com.joblivo.job.config.JobSourceProperties;
import com.joblivo.job.exception.DuplicateJobSourceAdapterException;
import com.joblivo.job.exception.JobConfigurationException;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.service.JobIngestionOrchestrator;
import com.joblivo.job.service.JobIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Job Source Coverage & Ingestion Safety Policy Tests")
class JobSourceCoveragePolicyTest {

    @Mock
    private JobSourceAdapter linkedinAdapter;

    @Mock
    private JobSourceAdapter naukriAdapter;

    @Mock
    private JobIngestionService jobIngestionService;

    private JobDiscoveryProperties properties;
    private JobSourceRegistry sourceRegistry;
    private JobSourceControlPolicy controlPolicy;
    private JobIngestionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        properties = new JobDiscoveryProperties();
        properties.setEnabled(true);
        properties.setDefaultMaxCandidates(100);
    }

    private void initSubsystems(List<JobSourceAdapter> adapters) {
        sourceRegistry = new JobSourceRegistry(adapters, properties);
        controlPolicy = new JobSourceControlPolicy(properties, sourceRegistry);
        orchestrator = new JobIngestionOrchestrator(sourceRegistry, controlPolicy, jobIngestionService);
    }

    @Nested
    @DisplayName("1. Coverage Status & Resolution Tests")
    class CoverageStatusTests {

        @Test
        @DisplayName("1. Implemented + enabled source is executable (SUPPORTED)")
        void implementedAndEnabled_isExecutableSupported() {
            when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);
            initSubsystems(List.of(linkedinAdapter));

            properties.setSource(JobSource.LINKEDIN, true, 50);

            JobSourceCoverage coverage = controlPolicy.getSourceCoverage(JobSource.LINKEDIN);
            assertThat(coverage.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(coverage.isImplemented()).isTrue();
            assertThat(coverage.implementationStatus()).isEqualTo(JobSourceImplementationStatus.IMPLEMENTED);
            assertThat(coverage.isConfiguredEnabled()).isTrue();
            assertThat(coverage.coverageStatus()).isEqualTo(JobSourceCoverageStatus.SUPPORTED);
            assertThat(coverage.isExecutionPermitted()).isTrue();
            assertThat(coverage.notPermittedReason()).isNull();

            JobSourceResolution resolution = controlPolicy.resolveSource(JobSource.LINKEDIN);
            assertThat(resolution.isExecutionAllowed()).isTrue();
            assertThat(resolution.hasAdapter()).isTrue();
            assertThat(resolution.adapter()).isSameAs(linkedinAdapter);
            assertThat(controlPolicy.isSourceEligible(JobSource.LINKEDIN)).isTrue();
        }

        @Test
        @DisplayName("2. Implemented + disabled source is not executable (CONFIGURED_BUT_DISABLED)")
        void implementedAndDisabled_isNotExecutable() {
            when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);
            initSubsystems(List.of(linkedinAdapter));

            properties.setSource(JobSource.LINKEDIN, false, 50);

            JobSourceCoverage coverage = controlPolicy.getSourceCoverage(JobSource.LINKEDIN);
            assertThat(coverage.isImplemented()).isTrue();
            assertThat(coverage.isConfiguredEnabled()).isFalse();
            assertThat(coverage.coverageStatus()).isEqualTo(JobSourceCoverageStatus.CONFIGURED_BUT_DISABLED);
            assertThat(coverage.isExecutionPermitted()).isFalse();
            assertThat(coverage.notPermittedReason()).contains("disabled in configuration");

            JobSourceResolution resolution = controlPolicy.resolveSource(JobSource.LINKEDIN);
            assertThat(resolution.isExecutionAllowed()).isFalse();
            assertThat(resolution.rejectionCategory()).isEqualTo("SOURCE_DISABLED");
            assertThat(controlPolicy.isSourceEligible(JobSource.LINKEDIN)).isFalse();
        }

        @Test
        @DisplayName("3. Unimplemented + disabled source is not executable (NOT_IMPLEMENTED)")
        void unimplementedAndDisabled_isNotExecutable() {
            initSubsystems(Collections.emptyList());

            properties.setSource(JobSource.FOUNDIT, false, 50);

            JobSourceCoverage coverage = controlPolicy.getSourceCoverage(JobSource.FOUNDIT);
            assertThat(coverage.isImplemented()).isFalse();
            assertThat(coverage.implementationStatus()).isEqualTo(JobSourceImplementationStatus.NOT_IMPLEMENTED);
            assertThat(coverage.coverageStatus()).isEqualTo(JobSourceCoverageStatus.NOT_IMPLEMENTED);
            assertThat(coverage.isExecutionPermitted()).isFalse();
            assertThat(coverage.notPermittedReason()).contains("not implemented");

            JobSourceResolution resolution = controlPolicy.resolveSource(JobSource.FOUNDIT);
            assertThat(resolution.isExecutionAllowed()).isFalse();
            assertThat(resolution.rejectionCategory()).isEqualTo("ADAPTER_NOT_FOUND");
            assertThat(controlPolicy.isSourceEligible(JobSource.FOUNDIT)).isFalse();
        }

        @Test
        @DisplayName("4. Unimplemented source cannot execute even if configuration requests enabled=true")
        void unimplementedSource_cannotExecuteEvenIfConfigEnabled() {
            initSubsystems(Collections.emptyList());

            // Deliberately request enabled=true in configuration for an unimplemented source
            properties.setSource(JobSource.CUTSHORT, true, 100);

            JobSourceCoverage coverage = controlPolicy.getSourceCoverage(JobSource.CUTSHORT);
            assertThat(coverage.isImplemented()).isFalse();
            assertThat(coverage.implementationStatus()).isEqualTo(JobSourceImplementationStatus.NOT_IMPLEMENTED);
            assertThat(coverage.isConfiguredEnabled()).isTrue();
            assertThat(coverage.coverageStatus()).isEqualTo(JobSourceCoverageStatus.NOT_IMPLEMENTED);
            // Invariant: UNIMPLEMENTED SOURCE == NEVER EXECUTABLE
            assertThat(coverage.isExecutionPermitted()).isFalse();
            assertThat(coverage.notPermittedReason()).contains("cannot execute even though configuration requests enabled=true");

            JobSourceResolution resolution = controlPolicy.resolveSource(JobSource.CUTSHORT);
            assertThat(resolution.isExecutionAllowed()).isFalse();
            assertThat(controlPolicy.isSourceEligible(JobSource.CUTSHORT)).isFalse();
        }

        @Test
        @DisplayName("7. Unknown/null source cannot execute (UNKNOWN)")
        void unknownSource_cannotExecute() {
            initSubsystems(Collections.emptyList());

            JobSourceCoverage coverage = controlPolicy.getSourceCoverage(null);
            assertThat(coverage.coverageStatus()).isEqualTo(JobSourceCoverageStatus.UNKNOWN);
            assertThat(coverage.isExecutionPermitted()).isFalse();
            assertThat(coverage.isImplemented()).isFalse();
            assertThat(coverage.notPermittedReason()).contains("JobSource must not be null");

            JobSourceResolution resolution = controlPolicy.resolveSource(null);
            assertThat(resolution.isExecutionAllowed()).isFalse();
            assertThat(resolution.rejectionCategory()).isEqualTo("INVALID_SOURCE");
        }

        @Test
        @DisplayName("10. Source resolution is deterministic and covers all known JobSource identities")
        void allKnownSourcesCoverageIsDeterministic() {
            when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);
            initSubsystems(List.of(linkedinAdapter));
            properties.setSource(JobSource.LINKEDIN, true, 50);

            List<JobSourceCoverage> allCoverage = controlPolicy.getAllSourceCoverage();
            assertThat(allCoverage).hasSize(JobSource.values().length);

            // Exactly LINKEDIN is SUPPORTED; all others without adapters are NOT_IMPLEMENTED
            for (JobSourceCoverage cov : allCoverage) {
                if (cov.source() == JobSource.LINKEDIN) {
                    assertThat(cov.coverageStatus()).isEqualTo(JobSourceCoverageStatus.SUPPORTED);
                    assertThat(cov.isExecutionPermitted()).isTrue();
                } else {
                    assertThat(cov.coverageStatus()).isEqualTo(JobSourceCoverageStatus.NOT_IMPLEMENTED);
                    assertThat(cov.isExecutionPermitted()).isFalse();
                }
            }
        }
    }

    @Nested
    @DisplayName("2. 7-Point Execution Policy Verification Tests")
    class ExecutionPolicyVerificationTests {

        @Test
        @DisplayName("5. Missing adapter is detected safely (ADAPTER_NOT_FOUND)")
        void missingAdapter_rejectedSafely() {
            initSubsystems(Collections.emptyList());
            properties.setSource(JobSource.INSTAHYRE, true, 50);

            JobSourceExecutionContext context = JobSourceExecutionContext.of("test-run", JobSource.INSTAHYRE, 50);
            JobSourceExecutionVerification verification = controlPolicy.verifyExecution(JobSource.INSTAHYRE, null, context);

            assertThat(verification.isValid()).isFalse();
            assertThat(verification.rejectionCategory()).isEqualTo("ADAPTER_NOT_FOUND");
            assertThat(verification.rejectionReason()).contains("No adapter registered for source: INSTAHYRE");
        }

        @Test
        @DisplayName("6. Adapter source mismatch is detected safely (SOURCE_MISMATCH)")
        void adapterSourceMismatch_rejectedSafely() {
            when(linkedinAdapter.source()).thenReturn(JobSource.NAUKRI); // Mismatch!
            initSubsystems(Collections.emptyList());

            JobSourceExecutionContext context = JobSourceExecutionContext.of("test-run", JobSource.LINKEDIN, 50);
            JobSourceExecutionVerification verification = controlPolicy.verifyExecution(JobSource.LINKEDIN, linkedinAdapter, context);

            assertThat(verification.isValid()).isFalse();
            assertThat(verification.rejectionCategory()).isEqualTo("SOURCE_MISMATCH");
            assertThat(verification.rejectionReason()).contains("does not match execution context source 'LINKEDIN'");
        }

        @Test
        @DisplayName("8. Invalid execution context cannot execute (null context or source mismatch)")
        void invalidExecutionContext_rejectedSafely() {
            when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);
            initSubsystems(List.of(linkedinAdapter));
            properties.setSource(JobSource.LINKEDIN, true, 50);

            // Null context
            JobSourceExecutionVerification v1 = controlPolicy.verifyExecution(JobSource.LINKEDIN, linkedinAdapter, null);
            assertThat(v1.isValid()).isFalse();
            assertThat(v1.rejectionCategory()).isEqualTo("INVALID_EXECUTION_CONTEXT");

            // Context source mismatch
            JobSourceExecutionContext mismatchCtx = JobSourceExecutionContext.of("test-run", JobSource.NAUKRI, 50);
            JobSourceExecutionVerification v2 = controlPolicy.verifyExecution(JobSource.LINKEDIN, linkedinAdapter, mismatchCtx);
            assertThat(v2.isValid()).isFalse();
            assertThat(v2.rejectionCategory()).isEqualTo("SOURCE_MISMATCH");
        }

        @Test
        @DisplayName("9. Invalid candidate limit cannot execute")
        void invalidCandidateLimit_rejectedSafely() {
            when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);
            initSubsystems(List.of(linkedinAdapter));
            properties.setSource(JobSource.LINKEDIN, true, 50);

            // JobSourceExecutionContext.of enforces positive candidate limit
            assertThatThrownBy(() -> JobSourceExecutionContext.of("test-run", JobSource.LINKEDIN, 0))
                    .isInstanceOf(IllegalArgumentException.class);

            // verifyExecution enforces MAX_CANDIDATES_UPPER_BOUND (1000)
            JobSourceExecutionContext excessCtx = JobSourceExecutionContext.of("test-run", JobSource.LINKEDIN, 1001);
            JobSourceExecutionVerification verification = controlPolicy.verifyExecution(JobSource.LINKEDIN, linkedinAdapter, excessCtx);
            assertThat(verification.isValid()).isFalse();
            assertThat(verification.rejectionCategory()).isEqualTo("INVALID_EXECUTION_CONTEXT");
            assertThat(verification.rejectionReason()).contains("must be between 1 and 1000");
        }
    }

    @Nested
    @DisplayName("3. Orchestrator Integration & Safety Invariants")
    class OrchestratorSafetyTests {

        @Test
        @DisplayName("15. Policy failure does not execute the adapter")
        void policyFailure_doesNotExecuteAdapter() {
            when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);
            initSubsystems(List.of(linkedinAdapter));
            properties.setSource(JobSource.LINKEDIN, false, 50); // disabled

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(result.failureSummaries()).hasSize(1);
            assertThat(result.failureSummaries().get(0).failureCategory()).isEqualTo("SOURCE_DISABLED");

            // Crucial safety guarantee: adapter is NEVER called
            verify(linkedinAdapter, never()).fetchIngestionCandidates(any());
        }

        @Test
        @DisplayName("16. Policy failure does not create fake jobs")
        void policyFailure_doesNotCreateFakeJobs() {
            initSubsystems(Collections.emptyList());

            IngestionRunResult result = orchestrator.ingestSource(JobSource.CUTSHORT);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(result.createdCount()).isEqualTo(0);
            assertThat(result.totalCandidates()).isEqualTo(0);

            // Crucial safety guarantee: persistence service is NEVER invoked
            verifyNoInteractions(jobIngestionService);
        }

        @Test
        @DisplayName("17. Policy failure does not make external network calls")
        void policyFailure_noNetworkCalls() {
            initSubsystems(Collections.emptyList());

            // Unimplemented source
            IngestionRunResult result = orchestrator.ingestSource(JobSource.OTHER);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(result.failureSummaries().get(0).failureCategory()).isEqualTo("ADAPTER_NOT_FOUND");
        }

        @Test
        @DisplayName("18. Existing ingestion lifecycle behavior remains correct for successful run")
        void successfulExecution_followsStandardLifecycle() {
            when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(Collections.emptyList());
            initSubsystems(List.of(linkedinAdapter));
            properties.setSource(JobSource.LINKEDIN, true, 50);

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED);
            assertThat(result.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(result.successfulCount()).isEqualTo(0);
            assertThat(result.failedCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("4. Registry & Configuration Safety Tests")
    class RegistryAndConfigurationTests {

        @Test
        @DisplayName("11. Registry remains the authoritative adapter source")
        void registryRemainsAuthoritative() {
            when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);
            initSubsystems(List.of(linkedinAdapter));

            assertThat(sourceRegistry.isSourceRegistered(JobSource.LINKEDIN)).isTrue();
            assertThat(sourceRegistry.isSourceRegistered(JobSource.NAUKRI)).isFalse();
            assertThat(sourceRegistry.getRegisteredSources()).containsExactly(JobSource.LINKEDIN);
        }

        @Test
        @DisplayName("12. No duplicate source registry: duplicate adapter registration throws exception")
        void duplicateAdapterRegistrationThrows() {
            when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);
            JobSourceAdapter duplicateAdapter = org.mockito.Mockito.mock(JobSourceAdapter.class);
            when(duplicateAdapter.source()).thenReturn(JobSource.LINKEDIN);

            assertThatThrownBy(() -> new JobSourceRegistry(List.of(linkedinAdapter, duplicateAdapter), properties))
                    .isInstanceOf(DuplicateJobSourceAdapterException.class)
                    .hasMessageContaining("Duplicate JobSourceAdapter registration for source 'LINKEDIN'");
        }

        @Test
        @DisplayName("13. All external sources remain disabled by default")
        void externalSourcesDisabledByDefault() {
            JobDiscoveryProperties freshProps = new JobDiscoveryProperties();
            freshProps.validateConfiguration();

            for (JobSource source : JobSource.values()) {
                assertThat(freshProps.isSourceEnabled(source)).isFalse();
            }
            assertThat(freshProps.getResolvedEnabledSources()).isEmpty();
        }

        @Test
        @DisplayName("14. Existing local operation with zero enabled external sources remains healthy")
        void zeroEnabledSources_isHealthy() {
            initSubsystems(Collections.emptyList());

            JobDiscoveryIngestionHealthIndicator indicator = new JobDiscoveryIngestionHealthIndicator(
                    properties, sourceRegistry, controlPolicy, orchestrator
            );

            Health health = indicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("readiness", JobDiscoveryIngestionHealthIndicator.READINESS_NO_SOURCES_ENABLED);
        }

        @Test
        @DisplayName("19. Existing source configuration validation continues working")
        void sourceConfigValidationEnforcesBounds() {
            JobDiscoveryProperties invalidProps = new JobDiscoveryProperties();
            invalidProps.setSources(Map.of(
                    "INVALID_SRC_NAME", new JobSourceProperties(true, 50)
            ));

            assertThatThrownBy(invalidProps::validateConfiguration)
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("Unsupported job source: 'INVALID_SRC_NAME'");
        }
    }
}
