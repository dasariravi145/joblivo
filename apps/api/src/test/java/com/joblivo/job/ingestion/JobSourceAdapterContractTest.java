package com.joblivo.job.ingestion;

import com.joblivo.job.config.JobDiscoveryProperties;
import com.joblivo.job.exception.DuplicateJobSourceAdapterException;
import com.joblivo.job.exception.JobSourceAdapterException;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import com.joblivo.job.service.JobIngestionOrchestrator;
import com.joblivo.job.service.JobIngestionService;
import com.joblivo.job.service.JobResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JobSourceAdapter Contract Hardening Tests")
class JobSourceAdapterContractTest {

    @Mock
    private JobIngestionService jobIngestionService;

    private JobDiscoveryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JobDiscoveryProperties();
        properties.setEnabled(true);

        JobResponse mockResponse = mock(JobResponse.class);
        when(jobIngestionService.ingestCandidate(any(JobIngestionCandidate.class)))
                .thenReturn(JobCandidateIngestionResult.created(mockResponse));
    }

    private JobIngestionCandidate createCandidate(JobSource source, String externalId, String title) {
        return JobIngestionCandidate.builder()
                .source(source)
                .externalJobId(externalId)
                .title(title)
                .companyName("Acme Corp")
                .description("Sample job description")
                .location("Bengaluru, India")
                .workMode(JobWorkMode.HYBRID)
                .employmentType(JobEmploymentType.FULL_TIME)
                .build();
    }

    @Nested
    @DisplayName("1 & 2. Source Identity & Candidate Retrieval Contract")
    class SourceIdentityAndRetrieval {

        @Test
        @DisplayName("Adapter exposes valid JobSource and retrieves candidates successfully")
        void adapterExposesValidSourceAndReturnsCandidates() {
            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    return List.of(createCandidate(JobSource.LINKEDIN, "lnk-1", "Backend Engineer"));
                }
            };

            assertThat(adapter.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(adapter.getSource()).isEqualTo(JobSource.LINKEDIN);

            JobSourceExecutionContext context = JobSourceExecutionContext.of("run-test-1", JobSource.LINKEDIN, 50);
            List<JobIngestionCandidate> candidates = adapter.fetchIngestionCandidates(context);
            assertThat(candidates).hasSize(1);
            assertThat(candidates.get(0).title()).isEqualTo("Backend Engineer");
        }

        @Test
        @DisplayName("Implementing fetchJobs delegates cleanly to fetchIngestionCandidates default")
        void fetchJobsDelegatesToFetchIngestionCandidates() {
            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource getSource() {
                    return JobSource.NAUKRI;
                }

                @Override
                public List<JobIngestionCandidate> fetchJobs(JobSourceExecutionContext context) {
                    return List.of(createCandidate(JobSource.NAUKRI, "naukri-1", "Java Lead"));
                }
            };

            JobSourceExecutionContext context = JobSourceExecutionContext.of("run-test-2", JobSource.NAUKRI, 25);
            List<JobIngestionCandidate> candidates = adapter.fetchIngestionCandidates(context);
            assertThat(candidates).hasSize(1);
            assertThat(candidates.get(0).source()).isEqualTo(JobSource.NAUKRI);
            assertThat(candidates.get(0).externalJobId()).isEqualTo("naukri-1");
            assertThat(candidates.get(0).title()).isEqualTo("Java Lead");
        }
    }

    @Nested
    @DisplayName("3 & 4. Empty & Null Result Normalization Boundary")
    class EmptyAndNullNormalization {

        @Test
        @DisplayName("3. Empty result is handled safely and returns completed with 0 candidates")
        void emptyResultHandledSafely() {
            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    return Collections.emptyList();
                }
            };

            properties.setSource(JobSource.LINKEDIN, true, 50);
            JobSourceRegistry registry = new JobSourceRegistry(List.of(adapter), properties);
            JobIngestionOrchestrator orchestrator = new JobIngestionOrchestrator(registry, jobIngestionService);

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED);
            assertThat(result.candidatesReceived()).isZero();
            assertThat(result.candidatesConsidered()).isZero();
            assertThat(result.successfulCount()).isZero();
            assertThat(result.failedCount()).isZero();
            verifyNoInteractions(jobIngestionService);
        }

        @Test
        @DisplayName("4. Null result returned by adapter is safely normalized at orchestrator boundary to empty list without NPE")
        void nullResultSafelyNormalizedAtBoundary() {
            JobSourceAdapter badAdapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    return null; // Violates recommendation, but must be safely normalized
                }
            };

            properties.setSource(JobSource.LINKEDIN, true, 50);
            JobSourceRegistry registry = new JobSourceRegistry(List.of(badAdapter), properties);
            JobIngestionOrchestrator orchestrator = new JobIngestionOrchestrator(registry, jobIngestionService);

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED);
            assertThat(result.candidatesReceived()).isZero();
            assertThat(result.candidatesConsidered()).isZero();
            verifyNoInteractions(jobIngestionService);
        }
    }

    @Nested
    @DisplayName("5 & 11. Failure Contract & Isolation")
    class FailureContractAndIsolation {

        @Test
        @DisplayName("5. JobSourceAdapterException communicates source, error category, and reaches orchestrator cleanly")
        void adapterExceptionReachesOrchestratorWithCategorizedFailure() {
            JobSourceAdapter failingAdapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.FOUNDIT;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.FOUNDIT;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    throw new JobSourceAdapterException(
                            JobSource.FOUNDIT,
                            IngestionErrorCategory.SOURCE_FAILURE,
                            "External portal returned HTTP 503 Service Unavailable"
                    );
                }
            };

            properties.setSource(JobSource.FOUNDIT, true, 50);
            JobSourceRegistry registry = new JobSourceRegistry(List.of(failingAdapter), properties);
            JobIngestionOrchestrator orchestrator = new JobIngestionOrchestrator(registry, jobIngestionService);

            IngestionRunResult result = orchestrator.ingestSource(JobSource.FOUNDIT);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.failureSummaries()).hasSize(1);
            CandidateFailureSummary summary = result.failureSummaries().get(0);
            assertThat(summary.failureCategory()).isEqualTo("SOURCE_FAILURE");
            assertThat(summary.safeMessage()).contains("HTTP 503");
        }

        @Test
        @DisplayName("11. Source failure does not abort unrelated source ingestion in multi-source run")
        void sourceFailureDoesNotAbortUnrelatedSources() {
            JobSourceAdapter failingAdapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    throw new JobSourceAdapterException(
                            JobSource.LINKEDIN,
                            IngestionErrorCategory.INFRASTRUCTURE,
                            "Connection timeout connecting to upstream endpoint"
                    );
                }
            };

            JobSourceAdapter healthyAdapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.NAUKRI;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.NAUKRI;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    return List.of(createCandidate(JobSource.NAUKRI, "nk-101", "Full Stack Developer"));
                }
            };

            properties.setSource(JobSource.LINKEDIN, true, 50);
            properties.setSource(JobSource.NAUKRI, true, 50);

            JobSourceRegistry registry = new JobSourceRegistry(List.of(failingAdapter, healthyAdapter), properties);
            JobIngestionOrchestrator orchestrator = new JobIngestionOrchestrator(registry, jobIngestionService);

            MultiSourceIngestionRunResult multiResult = orchestrator.ingestAllEnabledSources();

            assertThat(multiResult.status()).isEqualTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(multiResult.sourceResults()).hasSize(2);

            IngestionRunResult linkedinResult = multiResult.sourceResults().stream()
                    .filter(r -> r.source() == JobSource.LINKEDIN)
                    .findFirst()
                    .orElseThrow();
            assertThat(linkedinResult.status()).isEqualTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(linkedinResult.failedCount()).isEqualTo(1);

            IngestionRunResult naukriResult = multiResult.sourceResults().stream()
                    .filter(r -> r.source() == JobSource.NAUKRI)
                    .findFirst()
                    .orElseThrow();
            assertThat(naukriResult.status()).isEqualTo(IngestionRunStatus.COMPLETED);
            assertThat(naukriResult.successfulCount()).isEqualTo(1);
            assertThat(naukriResult.failedCount()).isZero();
        }
    }

    @Nested
    @DisplayName("6 & 7. Registry Compatibility & Duplicate Rejection")
    class RegistryCompatibility {

        @Test
        @DisplayName("6. Adapter source identity matches registry source resolution")
        void adapterSourceMatchesRegistry() {
            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.CUTSHORT;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.CUTSHORT;
                }
            };

            JobSourceRegistry registry = new JobSourceRegistry(List.of(adapter), properties);

            assertThat(registry.isSourceRegistered(JobSource.CUTSHORT)).isTrue();
            assertThat(registry.getAdapter(JobSource.CUTSHORT)).contains(adapter);
            assertThat(registry.getRegisteredSources()).contains(JobSource.CUTSHORT);
        }

        @Test
        @DisplayName("7. Duplicate source adapters remain rejected at registry initialization")
        void duplicateSourceAdaptersRejected() {
            JobSourceAdapter adapter1 = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }
            };

            JobSourceAdapter adapter2 = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }
            };

            assertThatThrownBy(() -> new JobSourceRegistry(List.of(adapter1, adapter2), properties))
                    .isInstanceOf(DuplicateJobSourceAdapterException.class)
                    .hasMessageContaining("Duplicate JobSourceAdapter registration for source 'LINKEDIN'");
        }
    }

    @Nested
    @DisplayName("8, 9 & 10. Orchestration Controls & Invariants")
    class OrchestrationControlsAndInvariants {

        @Test
        @DisplayName("8. Configured candidate limit is strictly enforced by orchestration, not adapter")
        void candidateLimitEnforcedByOrchestration() {
            List<JobIngestionCandidate> fiveCandidates = List.of(
                    createCandidate(JobSource.LINKEDIN, "1", "Title 1"),
                    createCandidate(JobSource.LINKEDIN, "2", "Title 2"),
                    createCandidate(JobSource.LINKEDIN, "3", "Title 3"),
                    createCandidate(JobSource.LINKEDIN, "4", "Title 4"),
                    createCandidate(JobSource.LINKEDIN, "5", "Title 5")
            );

            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    return fiveCandidates; // returns 5 candidates
                }
            };

            properties.setSource(JobSource.LINKEDIN, true, 3); // limit configured to 3

            JobSourceRegistry registry = new JobSourceRegistry(List.of(adapter), properties);
            JobIngestionOrchestrator orchestrator = new JobIngestionOrchestrator(registry, jobIngestionService);

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.candidatesReceived()).isEqualTo(5);
            assertThat(result.candidatesConsidered()).isEqualTo(3);
            assertThat(result.successfulCount()).isEqualTo(3);
            verify(jobIngestionService, org.mockito.Mockito.times(3)).ingestCandidate(any(JobIngestionCandidate.class));
        }

        @Test
        @DisplayName("9. Adapter candidate ordering is strictly preserved by the orchestrator")
        void adapterOrderingPreserved() {
            JobIngestionCandidate first = createCandidate(JobSource.LINKEDIN, "first-id", "First Title");
            JobIngestionCandidate second = createCandidate(JobSource.LINKEDIN, "second-id", "Second Title");
            JobIngestionCandidate third = createCandidate(JobSource.LINKEDIN, "third-id", "Third Title");

            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    return List.of(first, second, third);
                }
            };

            properties.setSource(JobSource.LINKEDIN, true, 100);
            JobSourceRegistry registry = new JobSourceRegistry(List.of(adapter), properties);
            JobIngestionOrchestrator orchestrator = new JobIngestionOrchestrator(registry, jobIngestionService);

            orchestrator.ingestSource(JobSource.LINKEDIN);

            ArgumentCaptor<JobIngestionCandidate> captor = ArgumentCaptor.forClass(JobIngestionCandidate.class);
            verify(jobIngestionService, org.mockito.Mockito.times(3)).ingestCandidate(captor.capture());

            List<JobIngestionCandidate> persisted = captor.getAllValues();
            assertThat(persisted.get(0).externalJobId()).isEqualTo("first-id");
            assertThat(persisted.get(1).externalJobId()).isEqualTo("second-id");
            assertThat(persisted.get(2).externalJobId()).isEqualTo("third-id");
        }

        @Test
        @DisplayName("10. Adapter does not persist jobs directly; persistence is delegated to JobIngestionService")
        void adapterDoesNotPersistJobsDirectly() {
            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.ATS;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.ATS;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    // Adapter only constructs candidates; zero database/persistence access
                    return List.of(createCandidate(JobSource.ATS, "ats-1", "Security Engineer"));
                }
            };

            properties.setSource(JobSource.ATS, true, 50);
            JobSourceRegistry registry = new JobSourceRegistry(List.of(adapter), properties);
            JobIngestionOrchestrator orchestrator = new JobIngestionOrchestrator(registry, jobIngestionService);

            orchestrator.ingestSource(JobSource.ATS);

            // Verified that the orchestrator invoked the persistence service
            verify(jobIngestionService).ingestCandidate(any(JobIngestionCandidate.class));
        }
    }

    @Nested
    @DisplayName("Execution Context Propagation & Source Consistency")
    class ExecutionContextPropagationAndConsistency {

        @Test
        @DisplayName("Propagates runId, source, and candidateLimit to adapter execution context")
        void contextPropagatedToAdapter() {
            AtomicReference<JobSourceExecutionContext> capturedContext = new AtomicReference<>();

            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    capturedContext.set(context);
                    return List.of();
                }
            };

            properties.setSource(JobSource.LINKEDIN, true, 42);
            JobSourceRegistry registry = new JobSourceRegistry(List.of(adapter), properties);
            JobIngestionOrchestrator orchestrator = new JobIngestionOrchestrator(registry, jobIngestionService);

            String expectedRunId = "run-prop-777";
            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN, JobFetchCriteria.of(null, null), expectedRunId);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED);
            assertThat(capturedContext.get()).isNotNull();
            assertThat(capturedContext.get().runId()).isEqualTo(expectedRunId);
            assertThat(capturedContext.get().source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(capturedContext.get().candidateLimit()).isEqualTo(42);
        }

        @Test
        @DisplayName("Rejects execution when adapter declared source does not match context source")
        void rejectsAdapterSourceMismatch() {
            // An adapter registered with a mismatch: declared source() is NAUKRI, but registered/targeted as LINKEDIN
            JobSourceAdapter mismatchedAdapter = new JobSourceAdapter() {
                @Override
                public JobSource source() {
                    return JobSource.NAUKRI;
                }

                @Override
                public JobSource getSource() {
                    return JobSource.NAUKRI;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    throw new AssertionError("Adapter must not be called when source mismatches");
                }
            };

            // Custom registry mapping LINKEDIN to mismatchedAdapter
            JobSourceRegistry registry = new JobSourceRegistry(List.of(), properties) {
                @Override
                public java.util.Optional<JobSourceAdapter> getAdapter(JobSource source) {
                    if (source == JobSource.LINKEDIN) {
                        return java.util.Optional.of(mismatchedAdapter);
                    }
                    return java.util.Optional.empty();
                }

                @Override
                public boolean isSourceRegistered(JobSource source) {
                    return source == JobSource.LINKEDIN;
                }
            };

            properties.setSource(JobSource.LINKEDIN, true, 50);
            JobIngestionOrchestrator orchestrator = new JobIngestionOrchestrator(registry, jobIngestionService);

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.candidatesReceived()).isZero();
            assertThat(result.failureSummaries()).hasSize(1);
            CandidateFailureSummary failure = result.failureSummaries().get(0);
            assertThat(failure.failureCategory()).isEqualTo("SOURCE_MISMATCH");
            assertThat(failure.safeMessage()).contains("does not match execution context source");
        }
    }

    @Nested
    @DisplayName("Security & Redaction Invariants")
    class SecurityInvariants {

        @Test
        @DisplayName("JobSourceAdapterException sanitizes bearer tokens, passwords, and secrets in messages")
        void adapterExceptionSanitizesSecrets() {
            JobSourceAdapterException ex1 = new JobSourceAdapterException(
                    JobSource.LINKEDIN,
                    "Failed request with Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz"
            );
            assertThat(ex1.getMessage()).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz");
            assertThat(ex1.getMessage()).contains("[REDACTED]");

            JobSourceAdapterException ex2 = new JobSourceAdapterException(
                    JobSource.NAUKRI,
                    "Auth failed with apiKey=secret_api_key_12345"
            );
            assertThat(ex2.getMessage()).doesNotContain("secret_api_key_12345");
            assertThat(ex2.getMessage()).contains("[REDACTED]");

            JobSourceAdapterException ex3 = new JobSourceAdapterException(
                    JobSource.FOUNDIT,
                    "Connection error: password: superSecretPassword123!"
            );
            assertThat(ex3.getMessage()).doesNotContain("superSecretPassword123!");
            assertThat(ex3.getMessage()).contains("[REDACTED]");
        }

        @Test
        @DisplayName("JobFetchCriteria sanitizes whitespace and enforces limit bounds")
        void jobFetchCriteriaSanitizesAndEnforcesBounds() {
            JobFetchCriteria criteria = new JobFetchCriteria("  engineer  ", "  Bengaluru  ", 5000);
            assertThat(criteria.query()).isEqualTo("engineer");
            assertThat(criteria.location()).isEqualTo("Bengaluru");
            assertThat(criteria.limit()).isEqualTo(JobFetchCriteria.MAX_LIMIT);

            JobFetchCriteria negativeLimit = new JobFetchCriteria("java", null, -10);
            assertThat(negativeLimit.limit()).isEqualTo(JobFetchCriteria.DEFAULT_LIMIT);

            JobFetchCriteria blankStrings = new JobFetchCriteria("   ", "   ", 25);
            assertThat(blankStrings.query()).isNull();
            assertThat(blankStrings.location()).isNull();
            assertThat(blankStrings.limit()).isEqualTo(25);
        }
    }
}
