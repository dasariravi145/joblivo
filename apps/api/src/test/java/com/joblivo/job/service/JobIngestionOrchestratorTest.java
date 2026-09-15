package com.joblivo.job.service;

import com.joblivo.job.config.JobDiscoveryProperties;
import com.joblivo.job.config.JobSourceProperties;
import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.ingestion.CandidateFailureSummary;
import com.joblivo.job.ingestion.CandidateIngestionAction;
import com.joblivo.job.ingestion.IngestionRunResult;
import com.joblivo.job.ingestion.IngestionRunStatus;
import com.joblivo.job.ingestion.JobIngestionMetrics;
import com.joblivo.job.ingestion.JobCandidate;
import com.joblivo.job.ingestion.JobCandidateIngestionResult;
import com.joblivo.job.ingestion.JobFetchCriteria;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.ingestion.JobSourceAdapter;
import com.joblivo.job.ingestion.JobSourceExecutionContext;
import com.joblivo.job.ingestion.JobSourceRegistry;
import com.joblivo.job.ingestion.MultiSourceIngestionRunResult;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JobIngestionOrchestrator Unit Tests")
class JobIngestionOrchestratorTest {

    @Mock
    private JobIngestionService jobIngestionService;

    @Mock
    private JobSourceAdapter linkedinAdapter;

    @Mock
    private JobSourceAdapter naukriAdapter;

    private JobDiscoveryProperties properties;
    private JobSourceRegistry registry;
    private JobIngestionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        properties = new JobDiscoveryProperties();
        properties.setEnabled(true);
        properties.setEnabledSources(Set.of(JobSource.LINKEDIN, JobSource.NAUKRI));

        when(linkedinAdapter.getSource()).thenReturn(JobSource.LINKEDIN);
        when(linkedinAdapter.source()).thenReturn(JobSource.LINKEDIN);

        when(naukriAdapter.getSource()).thenReturn(JobSource.NAUKRI);
        when(naukriAdapter.source()).thenReturn(JobSource.NAUKRI);

        registry = new JobSourceRegistry(List.of(linkedinAdapter, naukriAdapter), properties);
        orchestrator = new JobIngestionOrchestrator(registry, jobIngestionService);
    }

    private JobResponse mockJobResponse(UUID id, JobSource source, String externalId, String title) {
        return new JobResponse(
                id,
                source,
                externalId,
                title,
                "Acme Corp",
                "Alice Recruiter",
                "Software engineering description",
                "Remote",
                JobWorkMode.REMOTE,
                JobEmploymentType.FULL_TIME,
                2,
                5,
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(150000),
                "USD",
                SalaryPeriod.YEAR,
                "https://job.url/1",
                "https://company.url",
                Instant.now(),
                null,
                Instant.now(),
                Instant.now(),
                JobApplicationMethod.ATS,
                Instant.now(),
                Instant.now()
        );
    }

    private JobIngestionCandidate sampleCandidate(JobSource source, String externalId, String title) {
        return JobIngestionCandidate.builder()
                .source(source)
                .externalJobId(externalId)
                .title(title)
                .companyName("Acme Corp")
                .description("Sample job description")
                .build();
    }

    @Nested
    @DisplayName("Single Source Ingestion Validation & Safety")
    class SingleSourceSafetyTests {

        @Test
        @DisplayName("Null source returns failed IngestionRunResult without crashing")
        void nullSourceHandledSafely() {
            IngestionRunResult result = orchestrator.ingestSource(null);

            assertThat(result).isNotNull();
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.totalCandidates()).isEqualTo(0);
            assertThat(result.failureSummaries()).hasSize(1);
            assertThat(result.failureSummaries().get(0).failureCategory()).isEqualTo("INVALID_SOURCE");
        }

        @Test
        @DisplayName("Unregistered source returns ADAPTER_NOT_FOUND result without calling persistence")
        void unregisteredSourceReturnsAdapterNotFound() {
            IngestionRunResult result = orchestrator.ingestSource(JobSource.COMPANY_CAREERS);

            assertThat(result).isNotNull();
            assertThat(result.source()).isEqualTo(JobSource.COMPANY_CAREERS);
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.totalCandidates()).isEqualTo(0);
            assertThat(result.failureSummaries()).hasSize(1);
            assertThat(result.failureSummaries().get(0).failureCategory()).isEqualTo("ADAPTER_NOT_FOUND");
            verify(jobIngestionService, never()).ingestCandidate(any(JobIngestionCandidate.class));
        }

        @Test
        @DisplayName("Disabled source is not executed and returns SOURCE_DISABLED result")
        void disabledSourceNotExecuted() {
            properties.setEnabledSources(Set.of(JobSource.NAUKRI)); // LINKEDIN is disabled

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result).isNotNull();
            assertThat(result.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.totalCandidates()).isEqualTo(0);
            assertThat(result.failureSummaries()).hasSize(1);
            assertThat(result.failureSummaries().get(0).failureCategory()).isEqualTo("SOURCE_DISABLED");
            verify(linkedinAdapter, never()).fetchIngestionCandidates(any());
            verify(jobIngestionService, never()).ingestCandidate(any(JobIngestionCandidate.class));
        }

        @Test
        @DisplayName("Adapter throwing exception during fetch returns ADAPTER_FETCH_ERROR result")
        void adapterFetchErrorHandledSafely() {
            when(linkedinAdapter.fetchIngestionCandidates(any()))
                    .thenThrow(new RuntimeException("Upstream rate limit exceeded"));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result).isNotNull();
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.totalCandidates()).isEqualTo(0);
            assertThat(result.failureSummaries()).hasSize(1);
            CandidateFailureSummary failure = result.failureSummaries().get(0);
            assertThat(failure.failureCategory()).isEqualTo("ADAPTER_FETCH_ERROR");
            assertThat(failure.safeMessage()).contains("Upstream rate limit exceeded");
        }

        @Test
        @DisplayName("Adapter returning empty candidate list produces valid zero-count result")
        void emptyCandidateListHandledSafely() {
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(Collections.emptyList());

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result).isNotNull();
            assertThat(result.totalCandidates()).isEqualTo(0);
            assertThat(result.successfulCount()).isEqualTo(0);
            assertThat(result.failedCount()).isEqualTo(0);
            assertThat(result.skippedCount()).isEqualTo(0);
            assertThat(result.failureSummaries()).isEmpty();
        }

        @Test
        @DisplayName("Adapter returning null candidate list produces valid zero-count result")
        void nullCandidateListHandledSafely() {
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(null);

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result).isNotNull();
            assertThat(result.totalCandidates()).isEqualTo(0);
            assertThat(result.successfulCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Candidate-Level Failure Isolation & Batch Statistics")
    class CandidateIsolationTests {

        @Test
        @DisplayName("Candidate-level failures do not abort batch; statistics accurately partition created, updated, skipped, failed")
        void candidateFailureIsolationAndStatistics() {
            // Setup 5 candidates:
            // #1: Valid new candidate -> CREATED
            // #2: Valid existing candidate -> UPDATED
            // #3: Malformed candidate -> throws JobValidationException -> FAILED
            // #4: Duplicate of #1 in same batch -> SKIPPED
            // #5: Database constraint error -> throws DataIntegrityViolationException -> FAILED

            JobIngestionCandidate c1 = sampleCandidate(JobSource.LINKEDIN, "ext-101", "Backend Engineer");
            JobIngestionCandidate c2 = sampleCandidate(JobSource.LINKEDIN, "ext-102", "Senior Engineer");
            JobIngestionCandidate c3 = sampleCandidate(JobSource.LINKEDIN, "ext-103", "Invalid Job");
            JobIngestionCandidate c4 = sampleCandidate(JobSource.LINKEDIN, "ext-101", "Duplicate Backend Engineer");
            JobIngestionCandidate c5 = sampleCandidate(JobSource.LINKEDIN, "ext-105", "Constraint Violation Job");

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(c1, c2, c3, c4, c5));

            JobResponse response1 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "ext-101", "Backend Engineer");
            JobResponse response2 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "ext-102", "Senior Engineer");

            when(jobIngestionService.ingestCandidate(c1)).thenReturn(JobCandidateIngestionResult.created(response1));
            when(jobIngestionService.ingestCandidate(c2)).thenReturn(JobCandidateIngestionResult.updated(response2));
            when(jobIngestionService.ingestCandidate(c3)).thenThrow(new JobValidationException("salaryMin must not exceed salaryMax"));
            when(jobIngestionService.ingestCandidate(c5)).thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

            String customRunId = "run-test-42";
            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN, JobFetchCriteria.of(null, null), customRunId);

            assertThat(result).isNotNull();
            assertThat(result.runId()).isEqualTo("run-test-42");
            assertThat(result.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(result.totalCandidates()).isEqualTo(5);
            assertThat(result.createdCount()).isEqualTo(1);
            assertThat(result.updatedCount()).isEqualTo(1);
            assertThat(result.skippedCount()).isEqualTo(1); // duplicate in batch
            assertThat(result.duplicateCount()).isEqualTo(1);
            assertThat(result.failedCount()).isEqualTo(2);  // c3 and c5
            assertThat(result.successfulCount()).isEqualTo(2); // 1 created + 1 updated

            assertThat(result.failureSummaries()).hasSize(3); // 1 skipped duplicate + 2 failed

            // Verify safe diagnostics without leaking sensitive data
            CandidateFailureSummary validationSummary = result.failureSummaries().get(0);
            assertThat(validationSummary.failureCategory()).isEqualTo("VALIDATION_ERROR");
            assertThat(validationSummary.externalJobId()).isEqualTo("ext-103");
            assertThat(validationSummary.safeMessage()).contains("salaryMin must not exceed salaryMax");

            CandidateFailureSummary skipSummary = result.failureSummaries().get(1);
            assertThat(skipSummary.failureCategory()).isEqualTo("DUPLICATE_IN_BATCH");
            assertThat(skipSummary.externalJobId()).isEqualTo("ext-101");

            CandidateFailureSummary dbSummary = result.failureSummaries().get(2);
            assertThat(dbSummary.failureCategory()).isEqualTo("DATA_INTEGRITY_ERROR");
            assertThat(dbSummary.externalJobId()).isEqualTo("ext-105");
        }

        @Test
        @DisplayName("Batch with 100 candidates isolates failures and matches exact target distribution (90 success: 5 created, 85 updated, 3 skipped, 7 failed)")
        void exactPromptTargetDistribution() {
            List<JobIngestionCandidate> batch = new ArrayList<>(100);

            // 5 created
            for (int i = 1; i <= 5; i++) {
                batch.add(sampleCandidate(JobSource.LINKEDIN, "created-" + i, "Role " + i));
            }
            // 85 updated
            for (int i = 1; i <= 85; i++) {
                batch.add(sampleCandidate(JobSource.LINKEDIN, "updated-" + i, "Role " + i));
            }
            // 3 skipped (duplicates of created-1, created-2, created-3)
            for (int i = 1; i <= 3; i++) {
                batch.add(sampleCandidate(JobSource.LINKEDIN, "created-" + i, "Duplicate Role " + i));
            }
            // 7 failed
            for (int i = 1; i <= 7; i++) {
                batch.add(sampleCandidate(JobSource.LINKEDIN, "failed-" + i, "Bad Role " + i));
            }

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(batch);

            for (int i = 1; i <= 5; i++) {
                String extId = "created-" + i;
                JobResponse r = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, extId, "Role " + i);
                when(jobIngestionService.ingestCandidate(sampleCandidate(JobSource.LINKEDIN, extId, "Role " + i)))
                        .thenReturn(JobCandidateIngestionResult.created(r));
            }
            for (int i = 1; i <= 85; i++) {
                String extId = "updated-" + i;
                JobResponse r = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, extId, "Role " + i);
                when(jobIngestionService.ingestCandidate(sampleCandidate(JobSource.LINKEDIN, extId, "Role " + i)))
                        .thenReturn(JobCandidateIngestionResult.updated(r));
            }
            for (int i = 1; i <= 7; i++) {
                String extId = "failed-" + i;
                when(jobIngestionService.ingestCandidate(sampleCandidate(JobSource.LINKEDIN, extId, "Bad Role " + i)))
                        .thenThrow(new JobValidationException("Invalid candidate format " + i));
            }

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.totalCandidates()).isEqualTo(100);
            assertThat(result.successfulCount()).isEqualTo(90);
            assertThat(result.createdCount()).isEqualTo(5);
            assertThat(result.updatedCount()).isEqualTo(85);
            assertThat(result.skippedCount()).isEqualTo(3);
            assertThat(result.duplicateCount()).isEqualTo(3);
            assertThat(result.failedCount()).isEqualTo(7);
            assertThat(result.failureSummaries()).hasSize(10); // 3 skipped + 7 failed
        }

        @Test
        @DisplayName("Candidate with null or blank externalJobId is isolated as a failure")
        void nullOrBlankExternalIdHandledSafely() {
            JobIngestionCandidate nullExtId = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId(null)
                    .title("Engineer")
                    .build();

            JobIngestionCandidate blankExtId = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("   ")
                    .title("Engineer")
                    .build();

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(nullExtId, blankExtId));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.totalCandidates()).isEqualTo(2);
            assertThat(result.failedCount()).isEqualTo(2);
            assertThat(result.successfulCount()).isEqualTo(0);
            assertThat(result.failureSummaries()).allMatch(f -> f.failureCategory().equals("MISSING_EXTERNAL_ID"));
        }

        @Test
        @DisplayName("Null candidate in list is isolated as a failure")
        void nullCandidateInListHandledSafely() {
            List<JobIngestionCandidate> list = new ArrayList<>();
            list.add(null);

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(list);

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.totalCandidates()).isEqualTo(1);
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.failureSummaries().get(0).failureCategory()).isEqualTo("NULL_CANDIDATE");
        }

        @Test
        @DisplayName("Candidate with source mismatch is safely rejected without aborting remaining candidates")
        void candidateSourceMismatchWithinBatchRejectedSafely() {
            JobIngestionCandidate validCand = sampleCandidate(JobSource.LINKEDIN, "ext-valid", "Valid LinkedIn Candidate");
            JobIngestionCandidate mismatchedCand = sampleCandidate(JobSource.NAUKRI, "ext-mismatched", "Mismatched Candidate");

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(validCand, mismatchedCand));

            JobResponse response = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "ext-valid", "Valid LinkedIn Candidate");
            when(jobIngestionService.ingestCandidate(validCand)).thenReturn(JobCandidateIngestionResult.created(response));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.totalCandidates()).isEqualTo(2);
            assertThat(result.successfulCount()).isEqualTo(1);
            assertThat(result.createdCount()).isEqualTo(1);
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.failureSummaries()).hasSize(1);

            CandidateFailureSummary failure = result.failureSummaries().get(0);
            assertThat(failure.failureCategory()).isEqualTo("SOURCE_MISMATCH");
            assertThat(failure.externalJobId()).isEqualTo("ext-mismatched");
            assertThat(failure.safeMessage()).contains("does not match execution context source");

            verify(jobIngestionService, never()).ingestCandidate(mismatchedCand);
        }
    }

    @Nested
    @DisplayName("Idempotent Ingestion Execution")
    class IdempotencyTests {

        @Test
        @DisplayName("Repeated ingestion with same candidate reflects created on first run and updated on second run")
        void repeatedIngestionIdempotence() {
            JobIngestionCandidate candidate = sampleCandidate(JobSource.LINKEDIN, "ext-idem-1", "Lead Engineer");
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(candidate));

            JobResponse response = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "ext-idem-1", "Lead Engineer");

            // First run: created
            when(jobIngestionService.ingestCandidate(candidate))
                    .thenReturn(JobCandidateIngestionResult.created(response));

            IngestionRunResult run1 = orchestrator.ingestSource(JobSource.LINKEDIN);
            assertThat(run1.createdCount()).isEqualTo(1);
            assertThat(run1.updatedCount()).isEqualTo(0);
            assertThat(run1.successfulCount()).isEqualTo(1);

            // Second run: updated
            when(jobIngestionService.ingestCandidate(candidate))
                    .thenReturn(JobCandidateIngestionResult.updated(response));

            IngestionRunResult run2 = orchestrator.ingestSource(JobSource.LINKEDIN);
            assertThat(run2.createdCount()).isEqualTo(0);
            assertThat(run2.updatedCount()).isEqualTo(1);
            assertThat(run2.successfulCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Multi-Source Ingestion & Deterministic Ordering")
    class MultiSourceTests {

        @Test
        @DisplayName("Multi-source ingestion executes across all enabled sources in deterministic sorted order")
        void multiSourceExecutionDeterministicOrder() {
            JobIngestionCandidate linkedinCandidate = sampleCandidate(JobSource.LINKEDIN, "li-1", "LI Engineer");
            JobIngestionCandidate naukriCandidate = sampleCandidate(JobSource.NAUKRI, "nk-1", "Naukri Engineer");

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(linkedinCandidate));
            when(naukriAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(naukriCandidate));

            JobResponse r1 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "li-1", "LI Engineer");
            JobResponse r2 = mockJobResponse(UUID.randomUUID(), JobSource.NAUKRI, "nk-1", "Naukri Engineer");

            when(jobIngestionService.ingestCandidate(linkedinCandidate))
                    .thenReturn(JobCandidateIngestionResult.created(r1));
            when(jobIngestionService.ingestCandidate(naukriCandidate))
                    .thenReturn(JobCandidateIngestionResult.created(r2));

            String runId = "multi-run-888";
            MultiSourceIngestionRunResult result = orchestrator.ingestAllEnabledSources(JobFetchCriteria.of(null, null), runId);

            assertThat(result).isNotNull();
            assertThat(result.runId()).isEqualTo("multi-run-888");
            assertThat(result.totalSources()).isEqualTo(2);
            assertThat(result.totalCandidates()).isEqualTo(2);
            assertThat(result.totalCreated()).isEqualTo(2);
            assertThat(result.totalSuccessful()).isEqualTo(2);

            // Deterministic ordering: LINKEDIN before NAUKRI
            assertThat(result.sourceResults()).hasSize(2);
            assertThat(result.sourceResults().get(0).source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(result.sourceResults().get(1).source()).isEqualTo(JobSource.NAUKRI);
        }

        @Test
        @DisplayName("No enabled sources produces valid zero-count multi-source result")
        void noEnabledSourcesProducesZeroCount() {
            properties.setEnabledSources(Collections.emptySet());

            MultiSourceIngestionRunResult result = orchestrator.ingestAllEnabledSources();

            assertThat(result).isNotNull();
            assertThat(result.totalSources()).isEqualTo(0);
            assertThat(result.totalCandidates()).isEqualTo(0);
            assertThat(result.totalSuccessful()).isEqualTo(0);
            assertThat(result.sourceResults()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Run ID Propagation & Generation")
    class RunIdTests {

        @Test
        @DisplayName("Generates non-blank UUID run ID when caller does not provide one")
        void generatesRunIdWhenOmitted() {
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(Collections.emptyList());

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.runId()).isNotBlank();
            assertThat(UUID.fromString(result.runId())).isNotNull();
        }

        @Test
        @DisplayName("Preserves caller-provided correlation run ID")
        void preservesCallerRunId() {
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(Collections.emptyList());

            String customId = "correlation-" + UUID.randomUUID();
            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN, JobFetchCriteria.of(null, null), customId);

            assertThat(result.runId()).isEqualTo(customId);
        }
    }

    @Nested
    @DisplayName("Candidate Limit Enforcement & Partitioning")
    class CandidateLimitEnforcementTests {

        @Test
        @DisplayName("When adapter returns more candidates than limit, only first-N candidates are considered in order")
        void adapterReturnsMoreCandidatesThanLimit() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 3)
            ));
            properties.validateConfiguration();

            JobIngestionCandidate c1 = sampleCandidate(JobSource.LINKEDIN, "cand-1", "Role 1");
            JobIngestionCandidate c2 = sampleCandidate(JobSource.LINKEDIN, "cand-2", "Role 2");
            JobIngestionCandidate c3 = sampleCandidate(JobSource.LINKEDIN, "cand-3", "Role 3");
            JobIngestionCandidate c4 = sampleCandidate(JobSource.LINKEDIN, "cand-4", "Role 4");
            JobIngestionCandidate c5 = sampleCandidate(JobSource.LINKEDIN, "cand-5", "Role 5");

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(c1, c2, c3, c4, c5));

            JobResponse r1 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "cand-1", "Role 1");
            JobResponse r2 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "cand-2", "Role 2");
            JobResponse r3 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "cand-3", "Role 3");

            when(jobIngestionService.ingestCandidate(c1)).thenReturn(JobCandidateIngestionResult.created(r1));
            when(jobIngestionService.ingestCandidate(c2)).thenReturn(JobCandidateIngestionResult.created(r2));
            when(jobIngestionService.ingestCandidate(c3)).thenReturn(JobCandidateIngestionResult.created(r3));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.candidatesReceived()).isEqualTo(5);
            assertThat(result.candidatesConsidered()).isEqualTo(3);
            assertThat(result.totalCandidates()).isEqualTo(3);
            assertThat(result.createdCount()).isEqualTo(3);
            assertThat(result.successfulCount()).isEqualTo(3);

            // Deterministic first-N: c1, c2, c3 were ingested; c4, c5 were never passed to ingestion service
            verify(jobIngestionService).ingestCandidate(c1);
            verify(jobIngestionService).ingestCandidate(c2);
            verify(jobIngestionService).ingestCandidate(c3);
            verify(jobIngestionService, never()).ingestCandidate(c4);
            verify(jobIngestionService, never()).ingestCandidate(c5);
        }

        @Test
        @DisplayName("When adapter returns fewer candidates than limit, all candidates are considered")
        void adapterReturnsFewerCandidatesThanLimit() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 50)
            ));
            properties.validateConfiguration();

            JobIngestionCandidate c1 = sampleCandidate(JobSource.LINKEDIN, "cand-1", "Role 1");
            JobIngestionCandidate c2 = sampleCandidate(JobSource.LINKEDIN, "cand-2", "Role 2");

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(c1, c2));

            JobResponse r1 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "cand-1", "Role 1");
            JobResponse r2 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "cand-2", "Role 2");

            when(jobIngestionService.ingestCandidate(c1)).thenReturn(JobCandidateIngestionResult.created(r1));
            when(jobIngestionService.ingestCandidate(c2)).thenReturn(JobCandidateIngestionResult.created(r2));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.candidatesReceived()).isEqualTo(2);
            assertThat(result.candidatesConsidered()).isEqualTo(2);
            assertThat(result.totalCandidates()).isEqualTo(2);
            assertThat(result.createdCount()).isEqualTo(2);
            assertThat(result.successfulCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("When adapter returns exact limit, all candidates are considered without truncation")
        void adapterReturnsExactLimitCandidates() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 2)
            ));
            properties.validateConfiguration();

            JobIngestionCandidate c1 = sampleCandidate(JobSource.LINKEDIN, "cand-1", "Role 1");
            JobIngestionCandidate c2 = sampleCandidate(JobSource.LINKEDIN, "cand-2", "Role 2");

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(c1, c2));

            JobResponse r1 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "cand-1", "Role 1");
            JobResponse r2 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "cand-2", "Role 2");

            when(jobIngestionService.ingestCandidate(c1)).thenReturn(JobCandidateIngestionResult.created(r1));
            when(jobIngestionService.ingestCandidate(c2)).thenReturn(JobCandidateIngestionResult.created(r2));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.candidatesReceived()).isEqualTo(2);
            assertThat(result.candidatesConsidered()).isEqualTo(2);
            assertThat(result.totalCandidates()).isEqualTo(2);
            assertThat(result.createdCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Unregistered Enabled Source & Multi-Source Control")
    class UnregisteredEnabledSourceTests {

        @Test
        @DisplayName("Enabled source without registered adapter is safely reported during multi-source run without crashing")
        void enabledSourceWithoutAdapterReportedSafelyInMultiSource() {
            // LINKEDIN (adapter registered), CUTSHORT (no adapter registered)
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 10),
                    "CUTSHORT", new JobSourceProperties(true, 20)
            ));
            properties.validateConfiguration();

            JobIngestionCandidate liCand = sampleCandidate(JobSource.LINKEDIN, "li-1", "Role 1");
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(liCand));
            JobResponse r1 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "li-1", "Role 1");
            when(jobIngestionService.ingestCandidate(liCand)).thenReturn(JobCandidateIngestionResult.created(r1));

            MultiSourceIngestionRunResult result = orchestrator.ingestAllEnabledSources();

            assertThat(result).isNotNull();
            assertThat(result.totalSources()).isEqualTo(2);
            // Deterministic enum order: LINKEDIN before CUTSHORT
            assertThat(result.sourceResults()).hasSize(2);

            IngestionRunResult linkedinResult = result.sourceResults().get(0);
            assertThat(linkedinResult.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(linkedinResult.successfulCount()).isEqualTo(1);
            assertThat(linkedinResult.candidatesReceived()).isEqualTo(1);
            assertThat(linkedinResult.candidatesConsidered()).isEqualTo(1);

            IngestionRunResult cutshortResult = result.sourceResults().get(1);
            assertThat(cutshortResult.source()).isEqualTo(JobSource.CUTSHORT);
            assertThat(cutshortResult.failedCount()).isEqualTo(1);
            assertThat(cutshortResult.candidatesReceived()).isEqualTo(0);
            assertThat(cutshortResult.candidatesConsidered()).isEqualTo(0);
            assertThat(cutshortResult.failureSummaries()).hasSize(1);
            assertThat(cutshortResult.failureSummaries().get(0).failureCategory()).isEqualTo("ADAPTER_NOT_FOUND");

            assertThat(result.totalFailed()).isEqualTo(1);
            assertThat(result.totalSuccessful()).isEqualTo(1);
            assertThat(result.totalCandidatesReceived()).isEqualTo(1);
            assertThat(result.totalCandidatesConsidered()).isEqualTo(1);
        }

        @Test
        @DisplayName("Multiple source configurations with different limits remain deterministic")
        void multipleSourcesWithIndependentLimitsRemainDeterministic() {
            properties.setSources(Map.of(
                    "NAUKRI", new JobSourceProperties(true, 1),
                    "LINKEDIN", new JobSourceProperties(true, 2)
            ));
            properties.validateConfiguration();

            JobIngestionCandidate li1 = sampleCandidate(JobSource.LINKEDIN, "li-1", "Role 1");
            JobIngestionCandidate li2 = sampleCandidate(JobSource.LINKEDIN, "li-2", "Role 2");
            JobIngestionCandidate li3 = sampleCandidate(JobSource.LINKEDIN, "li-3", "Role 3");
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(li1, li2, li3));

            JobIngestionCandidate nk1 = sampleCandidate(JobSource.NAUKRI, "nk-1", "Role 1");
            JobIngestionCandidate nk2 = sampleCandidate(JobSource.NAUKRI, "nk-2", "Role 2");
            when(naukriAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(nk1, nk2));

            JobResponse rLi1 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "li-1", "Role 1");
            JobResponse rLi2 = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "li-2", "Role 2");
            JobResponse rNk1 = mockJobResponse(UUID.randomUUID(), JobSource.NAUKRI, "nk-1", "Role 1");

            when(jobIngestionService.ingestCandidate(li1)).thenReturn(JobCandidateIngestionResult.created(rLi1));
            when(jobIngestionService.ingestCandidate(li2)).thenReturn(JobCandidateIngestionResult.created(rLi2));
            when(jobIngestionService.ingestCandidate(nk1)).thenReturn(JobCandidateIngestionResult.created(rNk1));

            MultiSourceIngestionRunResult result = orchestrator.ingestAllEnabledSources();

            assertThat(result.totalSources()).isEqualTo(2);
            assertThat(result.sourceResults().get(0).source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(result.sourceResults().get(0).candidatesReceived()).isEqualTo(3);
            assertThat(result.sourceResults().get(0).candidatesConsidered()).isEqualTo(2);

            assertThat(result.sourceResults().get(1).source()).isEqualTo(JobSource.NAUKRI);
            assertThat(result.sourceResults().get(1).candidatesReceived()).isEqualTo(2);
            assertThat(result.sourceResults().get(1).candidatesConsidered()).isEqualTo(1);

            assertThat(result.totalCandidatesReceived()).isEqualTo(5);
            assertThat(result.totalCandidatesConsidered()).isEqualTo(3);
            assertThat(result.totalSuccessful()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Ingestion Run Lifecycle & Operational Status (Prompt 39)")
    class LifecycleAndOperationalStatusTests {

        @Test
        @DisplayName("Normal successful run produces COMPLETED status with valid timestamps and duration")
        void normalSuccessfulRunProducesCompletedStatus() {
            JobIngestionCandidate candidate = sampleCandidate(JobSource.LINKEDIN, "ext-ok-1", "Software Engineer");
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(candidate));
            JobResponse response = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "ext-ok-1", "Software Engineer");
            when(jobIngestionService.ingestCandidate(candidate)).thenReturn(JobCandidateIngestionResult.created(response));

            String customRunId = "run-lifecycle-normal";
            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN, JobFetchCriteria.of(null, null), customRunId);

            assertThat(result).isNotNull();
            assertThat(result.runId()).isEqualTo(customRunId);
            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED);
            assertThat(result.successfulCount()).isEqualTo(1);
            assertThat(result.failedCount()).isEqualTo(0);
            assertThat(result.startedAt()).isNotNull();
            assertThat(result.completedAt()).isNotNull();
            assertThat(result.completedAt()).isAfterOrEqualTo(result.startedAt());
            assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Run with zero candidates produces COMPLETED status (not failure)")
        void runWithZeroCandidatesProducesCompletedStatus() {
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(Collections.emptyList());

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED);
            assertThat(result.totalCandidates()).isEqualTo(0);
            assertThat(result.failedCount()).isEqualTo(0);
            assertThat(result.failureSummaries()).isEmpty();
        }

        @Test
        @DisplayName("Run with no enabled sources produces COMPLETED status with 0 sources")
        void runWithNoEnabledSourcesProducesCompletedStatus() {
            properties.setEnabledSources(Set.of());

            MultiSourceIngestionRunResult result = orchestrator.ingestAllEnabledSources();

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED);
            assertThat(result.totalSources()).isEqualTo(0);
            assertThat(result.totalCandidates()).isEqualTo(0);
            assertThat(result.totalFailed()).isEqualTo(0);
            assertThat(result.sourceResults()).isEmpty();
        }

        @Test
        @DisplayName("Candidate-level failure produces COMPLETED_WITH_ERRORS without failing entire run")
        void candidateLevelFailureProducesCompletedWithErrors() {
            JobIngestionCandidate validCand = sampleCandidate(JobSource.LINKEDIN, "ext-ok-2", "Valid Job");
            JobIngestionCandidate invalidCand = sampleCandidate(JobSource.LINKEDIN, "ext-err-1", "Invalid Job");

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(validCand, invalidCand));
            JobResponse response = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "ext-ok-2", "Valid Job");
            when(jobIngestionService.ingestCandidate(validCand)).thenReturn(JobCandidateIngestionResult.created(response));
            when(jobIngestionService.ingestCandidate(invalidCand)).thenThrow(new JobValidationException("Invalid title"));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(result.successfulCount()).isEqualTo(1);
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.failureSummaries()).hasSize(1);
            assertThat(result.failureSummaries().get(0).failureCategory()).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("One source failure does not erase another source's successful result in multi-source run")
        void oneSourceFailureDoesNotEraseAnotherSourceSuccess() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 10),
                    "NAUKRI", new JobSourceProperties(true, 10)
            ));
            properties.validateConfiguration();

            JobIngestionCandidate liCand = sampleCandidate(JobSource.LINKEDIN, "li-succ-1", "Staff Engineer");
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(liCand));
            JobResponse response = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "li-succ-1", "Staff Engineer");
            when(jobIngestionService.ingestCandidate(liCand)).thenReturn(JobCandidateIngestionResult.created(response));

            // Naukri adapter throws exception during fetch (source-level failure)
            when(naukriAdapter.fetchIngestionCandidates(any())).thenThrow(new RuntimeException("Naukri gateway timeout"));

            String customRunId = "multi-partial-failure-run";
            MultiSourceIngestionRunResult result = orchestrator.ingestAllEnabledSources(JobFetchCriteria.of(null, null), customRunId);

            assertThat(result).isNotNull();
            assertThat(result.runId()).isEqualTo(customRunId);
            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(result.totalSources()).isEqualTo(2);
            assertThat(result.totalSuccessful()).isEqualTo(1);
            assertThat(result.totalFailed()).isEqualTo(1);

            // LINKEDIN result retained and successful
            IngestionRunResult linkedinResult = result.sourceResults().get(0);
            assertThat(linkedinResult.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(linkedinResult.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED);
            assertThat(linkedinResult.successfulCount()).isEqualTo(1);
            assertThat(linkedinResult.runId()).isEqualTo(customRunId);

            // NAUKRI result safely represented with error
            IngestionRunResult naukriResult = result.sourceResults().get(1);
            assertThat(naukriResult.source()).isEqualTo(JobSource.NAUKRI);
            assertThat(naukriResult.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(naukriResult.failedCount()).isEqualTo(1);
            assertThat(naukriResult.failureSummaries().get(0).failureCategory()).isEqualTo("ADAPTER_FETCH_ERROR");
            assertThat(naukriResult.runId()).isEqualTo(customRunId);
        }

        @Test
        @DisplayName("Fatal orchestration failure produces FAILED status")
        void fatalOrchestrationFailureProducesFailedStatus() {
            // Null source represents fatal/invalid orchestration invocation
            IngestionRunResult result = orchestrator.ingestSource(null);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.FAILED);
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.failureSummaries().get(0).failureCategory()).isEqualTo("INVALID_SOURCE");
        }

        @Test
        @DisplayName("Run ID remains consistent throughout execution across all sources")
        void runIdRemainsConsistentThroughoutExecution() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 5),
                    "NAUKRI", new JobSourceProperties(true, 5)
            ));
            properties.validateConfiguration();

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(Collections.emptyList());
            when(naukriAdapter.fetchIngestionCandidates(any())).thenReturn(Collections.emptyList());

            String explicitRunId = "consistent-run-uuid-999";
            MultiSourceIngestionRunResult result = orchestrator.ingestAllEnabledSources(JobFetchCriteria.of(null, null), explicitRunId);

            assertThat(result.runId()).isEqualTo(explicitRunId);
            assertThat(result.sourceResults()).hasSize(2);
            for (IngestionRunResult singleResult : result.sourceResults()) {
                assertThat(singleResult.runId()).isEqualTo(explicitRunId);
            }
        }
    }

    @Nested
    @DisplayName("Ingestion Metrics & Operational Observability (Prompt 40)")
    class IngestionMetricsIntegrationTests {

        private io.micrometer.core.instrument.simple.SimpleMeterRegistry testMeterRegistry;

        @BeforeEach
        void initMetrics() {
            testMeterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            JobIngestionMetrics metrics = new JobIngestionMetrics(testMeterRegistry);
            orchestrator = new JobIngestionOrchestrator(
                    registry,
                    new com.joblivo.job.ingestion.JobSourceControlPolicy(properties, registry),
                    jobIngestionService,
                    metrics
            );
        }

        @Test
        @DisplayName("Single source success records run, source, candidate, and duration metrics")
        void singleSourceSuccessRecordsAllMetrics() {
            JobIngestionCandidate candidate = sampleCandidate(JobSource.LINKEDIN, "ext-met-1", "Lead Engineer");
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(candidate));
            JobResponse response = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "ext-met-1", "Lead Engineer");
            when(jobIngestionService.ingestCandidate(candidate)).thenReturn(JobCandidateIngestionResult.created(response));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED);

            // Run-level metrics
            io.micrometer.core.instrument.Counter runStarted = testMeterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_STARTED).counter();
            assertThat(runStarted).isNotNull();
            assertThat(runStarted.count()).isEqualTo(1.0);

            io.micrometer.core.instrument.Counter runCompleted = testMeterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED).counter();
            assertThat(runCompleted).isNotNull();
            assertThat(runCompleted.count()).isEqualTo(1.0);

            io.micrometer.core.instrument.Timer runTimer = testMeterRegistry.find(JobIngestionMetrics.METRIC_RUNS_DURATION)
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED).timer();
            assertThat(runTimer).isNotNull();
            assertThat(runTimer.count()).isEqualTo(1);

            // Source-level metrics
            io.micrometer.core.instrument.Counter srcStarted = testMeterRegistry.find(JobIngestionMetrics.METRIC_SOURCES)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_STARTED).counter();
            assertThat(srcStarted).isNotNull();
            assertThat(srcStarted.count()).isEqualTo(1.0);

            io.micrometer.core.instrument.Counter srcCompleted = testMeterRegistry.find(JobIngestionMetrics.METRIC_SOURCES)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED).counter();
            assertThat(srcCompleted).isNotNull();
            assertThat(srcCompleted.count()).isEqualTo(1.0);

            io.micrometer.core.instrument.Timer srcTimer = testMeterRegistry.find(JobIngestionMetrics.METRIC_SOURCES_DURATION)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED).timer();
            assertThat(srcTimer).isNotNull();
            assertThat(srcTimer.count()).isEqualTo(1);

            // Candidate processing metrics
            assertThat(testMeterRegistry.find(JobIngestionMetrics.METRIC_CANDIDATES_RECEIVED)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN").counter().count()).isEqualTo(1.0);
            assertThat(testMeterRegistry.find(JobIngestionMetrics.METRIC_CANDIDATES_CONSIDERED)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN").counter().count()).isEqualTo(1.0);
            assertThat(testMeterRegistry.find(JobIngestionMetrics.METRIC_JOBS_CREATED)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Candidate failure records completed_with_errors and categorized error metric")
        void candidateFailureRecordsErrorsAndMetric() {
            JobIngestionCandidate validCand = sampleCandidate(JobSource.LINKEDIN, "ext-val-1", "Valid");
            JobIngestionCandidate errCand = sampleCandidate(JobSource.LINKEDIN, "ext-err-1", "Invalid");

            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(validCand, errCand));
            JobResponse response = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "ext-val-1", "Valid");
            when(jobIngestionService.ingestCandidate(validCand)).thenReturn(JobCandidateIngestionResult.created(response));
            when(jobIngestionService.ingestCandidate(errCand)).thenThrow(new JobValidationException("Invalid title"));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED_WITH_ERRORS);

            io.micrometer.core.instrument.Counter runWithErrors = testMeterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED_WITH_ERRORS).counter();
            assertThat(runWithErrors).isNotNull();
            assertThat(runWithErrors.count()).isEqualTo(1.0);

            io.micrometer.core.instrument.Counter srcWithErrors = testMeterRegistry.find(JobIngestionMetrics.METRIC_SOURCES)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED_WITH_ERRORS).counter();
            assertThat(srcWithErrors).isNotNull();
            assertThat(srcWithErrors.count()).isEqualTo(1.0);

            io.micrometer.core.instrument.Counter failedCand = testMeterRegistry.find(JobIngestionMetrics.METRIC_CANDIDATES_FAILED)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN").counter();
            assertThat(failedCand).isNotNull();
            assertThat(failedCand.count()).isEqualTo(1.0);

            io.micrometer.core.instrument.Counter errCategory = testMeterRegistry.find(JobIngestionMetrics.METRIC_ERRORS)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                    .tag(JobIngestionMetrics.TAG_ERROR_CATEGORY, "VALIDATION").counter();
            assertThat(errCategory).isNotNull();
            assertThat(errCategory.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Source failure records SOURCE_FAILURE error metric and completed_with_errors")
        void sourceFailureRecordsSourceErrorMetric() {
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenThrow(new RuntimeException("Gateway timeout"));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED_WITH_ERRORS);

            io.micrometer.core.instrument.Counter errCategory = testMeterRegistry.find(JobIngestionMetrics.METRIC_ERRORS)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                    .tag(JobIngestionMetrics.TAG_ERROR_CATEGORY, "SOURCE_FAILURE").counter();
            assertThat(errCategory).isNotNull();
            assertThat(errCategory.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Fatal orchestration failure records FAILED run and CONFIGURATION/INFRASTRUCTURE metric")
        void fatalFailureRecordsFailedMetric() {
            IngestionRunResult result = orchestrator.ingestSource(null);

            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.FAILED);

            io.micrometer.core.instrument.Counter failedRun = testMeterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_FAILED).counter();
            assertThat(failedRun).isNotNull();
            assertThat(failedRun.count()).isEqualTo(1.0);

            io.micrometer.core.instrument.Counter configErr = testMeterRegistry.find(JobIngestionMetrics.METRIC_ERRORS)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "UNKNOWN")
                    .tag(JobIngestionMetrics.TAG_ERROR_CATEGORY, "CONFIGURATION").counter();
            assertThat(configErr).isNotNull();
            assertThat(configErr.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Multi-source ingestion increments exactly ONE run outcome and records each source individually")
        void multiSourceRecordsExactlyOneRunOutcome() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 10),
                    "NAUKRI", new JobSourceProperties(true, 10)
            ));
            properties.validateConfiguration();

            JobIngestionCandidate liCand = sampleCandidate(JobSource.LINKEDIN, "li-m-1", "Role 1");
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(List.of(liCand));
            JobResponse response = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, "li-m-1", "Role 1");
            when(jobIngestionService.ingestCandidate(liCand)).thenReturn(JobCandidateIngestionResult.created(response));

            when(naukriAdapter.fetchIngestionCandidates(any())).thenThrow(new RuntimeException("Naukri failure"));

            MultiSourceIngestionRunResult result = orchestrator.ingestAllEnabledSources();

            assertThat(result.status()).isEqualTo(com.joblivo.job.ingestion.IngestionRunStatus.COMPLETED_WITH_ERRORS);

            // Total runs: exactly 1 run started, exactly 1 run completed_with_errors
            assertThat(testMeterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_STARTED).counter().count()).isEqualTo(1.0);
            assertThat(testMeterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED_WITH_ERRORS).counter().count()).isEqualTo(1.0);
            assertThat(testMeterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED).counter()).isNull();
            assertThat(testMeterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_FAILED).counter()).isNull();

            // Source-level metrics: 2 sources executed
            assertThat(testMeterRegistry.find(JobIngestionMetrics.METRIC_SOURCES)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED).counter().count()).isEqualTo(1.0);
            assertThat(testMeterRegistry.find(JobIngestionMetrics.METRIC_SOURCES)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "NAUKRI")
                    .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED_WITH_ERRORS).counter().count()).isEqualTo(1.0);

            // Errors: Naukri has 1 SOURCE_FAILURE error
            assertThat(testMeterRegistry.find(JobIngestionMetrics.METRIC_ERRORS)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "NAUKRI")
                    .tag(JobIngestionMetrics.TAG_ERROR_CATEGORY, "SOURCE_FAILURE").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Tag cardinality review: No high-cardinality tags (runId, jobId, externalJobId) exist in registry")
        void verifyNoHighCardinalityTags() {
            orchestrator.ingestSource(JobSource.LINKEDIN);
            orchestrator.ingestAllEnabledSources();

            Set<String> allowedTags = Set.of(
                    JobIngestionMetrics.TAG_SOURCE,
                    JobIngestionMetrics.TAG_STATUS,
                    JobIngestionMetrics.TAG_ERROR_CATEGORY
            );

            for (io.micrometer.core.instrument.Meter meter : testMeterRegistry.getMeters()) {
                for (io.micrometer.core.instrument.Tag tag : meter.getId().getTags()) {
                    assertThat(allowedTags)
                            .as("Tag key '%s' on meter '%s' must be in allowed low-cardinality set", tag.getKey(), meter.getId().getName())
                            .contains(tag.getKey());
                }
            }
        }
    }

    @Nested
    @DisplayName("Job Discovery Source Adapter Execution Context (Prompt 43)")
    class JobSourceExecutionContextTests {

        @Test
        @DisplayName("Propagates correlation runId, source, and candidateLimit to adapter execution context")
        void propagatesExecutionContextToAdapter() {
            ArgumentCaptor<JobSourceExecutionContext> captor = ArgumentCaptor.forClass(JobSourceExecutionContext.class);
            when(linkedinAdapter.fetchIngestionCandidates(captor.capture())).thenReturn(Collections.emptyList());

            String explicitRunId = "run-orch-ctx-42";
            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN, JobFetchCriteria.of(null, null), explicitRunId);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED);
            JobSourceExecutionContext context = captor.getValue();
            assertThat(context).isNotNull();
            assertThat(context.runId()).isEqualTo(explicitRunId);
            assertThat(context.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(context.candidateLimit()).isEqualTo(100); // defaultMaxCandidates
        }

        @Test
        @DisplayName("Propagates source-specific configured candidate limit to execution context")
        void propagatesConfiguredCandidateLimit() {
            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 75)
            ));
            properties.validateConfiguration();

            ArgumentCaptor<JobSourceExecutionContext> captor = ArgumentCaptor.forClass(JobSourceExecutionContext.class);
            when(linkedinAdapter.fetchIngestionCandidates(captor.capture())).thenReturn(Collections.emptyList());

            orchestrator.ingestSource(JobSource.LINKEDIN);

            JobSourceExecutionContext context = captor.getValue();
            assertThat(context).isNotNull();
            assertThat(context.candidateLimit()).isEqualTo(75);
        }

        @Test
        @DisplayName("Rejects execution when adapter declared source does not match execution context source")
        void rejectsAdapterSourceMismatch() {
            when(linkedinAdapter.source()).thenReturn(JobSource.NAUKRI); // returns NAUKRI but registered for LINKEDIN

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.failureSummaries()).hasSize(1);

            CandidateFailureSummary failure = result.failureSummaries().get(0);
            assertThat(failure.failureCategory()).isEqualTo("SOURCE_MISMATCH");
            assertThat(failure.safeMessage()).contains("does not match execution context source");

            verify(linkedinAdapter, never()).fetchIngestionCandidates(any());
            verifyNoInteractions(jobIngestionService);
        }

        @Test
        @DisplayName("Authoritative orchestrator candidate limit remains enforced even if adapter returns excess candidates")
        void candidateLimitEnforcedAuthoritatively() {
            List<JobIngestionCandidate> excess = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                excess.add(sampleCandidate(JobSource.LINKEDIN, "cand-" + i, "Role " + i));
            }
            when(linkedinAdapter.fetchIngestionCandidates(any())).thenReturn(excess);

            properties.setSources(Map.of(
                    "LINKEDIN", new JobSourceProperties(true, 4)
            ));
            properties.validateConfiguration();

            for (int i = 1; i <= 4; i++) {
                String extId = "cand-" + i;
                JobResponse r = mockJobResponse(UUID.randomUUID(), JobSource.LINKEDIN, extId, "Role " + i);
                when(jobIngestionService.ingestCandidate(sampleCandidate(JobSource.LINKEDIN, extId, "Role " + i)))
                        .thenReturn(JobCandidateIngestionResult.created(r));
            }

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN);

            assertThat(result.candidatesReceived()).isEqualTo(10);
            assertThat(result.candidatesConsidered()).isEqualTo(4);
            assertThat(result.successfulCount()).isEqualTo(4);
            verify(jobIngestionService, org.mockito.Mockito.times(4)).ingestCandidate(any(JobIngestionCandidate.class));
        }
    }
}


