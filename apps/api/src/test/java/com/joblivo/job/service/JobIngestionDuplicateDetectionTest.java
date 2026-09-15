package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.JobRepository;
import com.joblivo.job.duplicate.JobDuplicateClassification;
import com.joblivo.job.duplicate.JobDuplicateDetector;
import com.joblivo.job.duplicate.JobDuplicateResult;
import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.ingestion.CandidateFailureSummary;
import com.joblivo.job.ingestion.CandidateIngestionAction;
import com.joblivo.job.ingestion.IngestionRunResult;
import com.joblivo.job.ingestion.JobCandidateIngestionResult;
import com.joblivo.job.ingestion.JobFetchCriteria;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.ingestion.JobIngestionMetrics;
import com.joblivo.job.ingestion.JobSourceAdapter;
import com.joblivo.job.ingestion.JobSourceControlPolicy;
import com.joblivo.job.ingestion.JobSourceExecutionContext;
import com.joblivo.job.ingestion.JobSourceRegistry;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobSourceIdentity;
import com.joblivo.job.normalizer.DefaultJobNormalizer;
import com.joblivo.job.validation.JobIngestionValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Job Ingestion Duplicate Detection Integration Tests")
class JobIngestionDuplicateDetectionTest {

    @Mock
    private JobRepository jobRepository;

    private DefaultJobNormalizer normalizer;
    private JobIngestionValidator validator;
    private JobDuplicateDetector duplicateDetector;
    private JobIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        normalizer = new DefaultJobNormalizer();
        validator = new JobIngestionValidator();
        duplicateDetector = new JobDuplicateDetector(jobRepository);
        ingestionService = new JobIngestionService(jobRepository, normalizer, validator, duplicateDetector);
    }

    private Job createStoredJob(JobSource source, String externalId, String title, String company, String location, String description, String url) {
        Job job = new Job(source, externalId, title, company);
        job.setLocation(location);
        job.setDescription(description);
        job.setJobUrl(url);
        return job;
    }

    private JobIngestionCandidate createCandidate(
            JobSource source, String externalId, String title, String company, String location, String description, String url
    ) {
        return JobIngestionCandidate.builder()
                .source(source)
                .externalJobId(externalId)
                .title(title)
                .companyName(company)
                .location(location)
                .description(description)
                .jobUrl(url)
                .build();
    }

    @Nested
    @DisplayName("Ingestion Duplicate Signal Handling")
    class IngestionDuplicateSignalTests {

        @Test
        @DisplayName("1. EXACT_SOURCE_DUPLICATE: Updates existing job idempotently without creating second row")
        void exactSourceDuplicate_updatesExistingJobIdempotently() {
            Job existingJob = createStoredJob(JobSource.LINKEDIN, "LI-100", "Old Title", "Acme Corp", "Remote", "Old Desc", "https://example.com/job");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "LI-100"))
                    .thenReturn(Optional.of(existingJob));
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            JobIngestionCandidate candidate = createCandidate(JobSource.LINKEDIN, "LI-100", "New Title", "Acme Corp", "Hybrid", "New Desc", "https://example.com/job");

            JobCandidateIngestionResult result = ingestionService.ingestCandidate(candidate);

            assertThat(result.action()).isEqualTo(CandidateIngestionAction.UPDATED);
            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.isSourceDuplicate()).isTrue();
            assertThat(result.isCrossSourceDuplicate()).isFalse();
            assertThat(result.duplicateResult().classification()).isEqualTo(JobDuplicateClassification.EXACT_SOURCE_DUPLICATE);
            assertThat(result.job().title()).isEqualTo("New Title");
            assertThat(result.job().source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(result.job().externalJobId()).isEqualTo("LI-100");

            // Verify exact single save on the existing entity
            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue()).isSameAs(existingJob);
        }

        @Test
        @DisplayName("2. EXACT_URL_DUPLICATE: Detects duplicate, preserves both records and provenance, creates new row")
        void exactUrlDuplicate_preservesBothRecordsAndProvenance() {
            Job existingLinkedinJob = createStoredJob(JobSource.LINKEDIN, "LI-100", "Staff Engineer", "Google", "Mountain View", "Build Search", "https://careers.google.com/jobs/1");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, "NK-200"))
                    .thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://careers.google.com/jobs/1"))
                    .thenReturn(Optional.of(existingLinkedinJob));
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            JobIngestionCandidate naukriCandidate = createCandidate(JobSource.NAUKRI, "NK-200", "Staff Engineer", "Google", "Mountain View", "Build Search", "https://careers.google.com/jobs/1");

            JobCandidateIngestionResult result = ingestionService.ingestCandidate(naukriCandidate);

            assertThat(result.action()).isEqualTo(CandidateIngestionAction.DETECTED_CROSS_SOURCE_DUPLICATE);
            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.isCrossSourceDuplicate()).isTrue();
            assertThat(result.duplicateResult().classification()).isEqualTo(JobDuplicateClassification.EXACT_URL_DUPLICATE);
            assertThat(result.duplicateResult().evidenceDetail()).isEqualTo("https://careers.google.com/jobs/1");

            // Provenance of new job is strictly NAUKRI
            assertThat(result.job().source()).isEqualTo(JobSource.NAUKRI);
            assertThat(result.job().externalJobId()).isEqualTo("NK-200");

            // Stored LinkedIn job is NOT merged or mutated
            assertThat(existingLinkedinJob.getSource()).isEqualTo(JobSource.LINKEDIN);
            assertThat(existingLinkedinJob.getExternalJobId()).isEqualTo("LI-100");

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            Job savedNewJob = captor.getValue();
            assertThat(savedNewJob).isNotSameAs(existingLinkedinJob);
            assertThat(savedNewJob.getSource()).isEqualTo(JobSource.NAUKRI);
            assertThat(savedNewJob.getExternalJobId()).isEqualTo("NK-200");
        }

        @Test
        @DisplayName("3. EXACT_CONTENT_DUPLICATE: Detects duplicate, preserves both records, creates second record")
        void exactContentDuplicate_preservesBothRecordsAndProvenance() {
            Job existingLinkedinJob = createStoredJob(
                    JobSource.LINKEDIN, "LI-500", "Principal Cloud Architect", "Microsoft", "Redmond", "Design Azure services", "https://linkedin.com/jobs/500"
            );

            when(jobRepository.findBySourceAndExternalJobId(JobSource.CUTSHORT, "CS-800"))
                    .thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://cutshort.io/jobs/800"))
                    .thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("Principal Cloud Architect", "Microsoft"))
                    .thenReturn(List.of(existingLinkedinJob));
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            JobIngestionCandidate cutshortCandidate = createCandidate(
                    JobSource.CUTSHORT, "CS-800", "Principal   Cloud Architect", "Microsoft", "Redmond", "Design Azure services", "https://cutshort.io/jobs/800"
            );

            JobCandidateIngestionResult result = ingestionService.ingestCandidate(cutshortCandidate);

            assertThat(result.action()).isEqualTo(CandidateIngestionAction.DETECTED_CROSS_SOURCE_DUPLICATE);
            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.isCrossSourceDuplicate()).isTrue();
            assertThat(result.duplicateResult().classification()).isEqualTo(JobDuplicateClassification.EXACT_CONTENT_DUPLICATE);

            // Verify new record is created with Cutshort provenance
            assertThat(result.job().source()).isEqualTo(JobSource.CUTSHORT);
            assertThat(result.job().externalJobId()).isEqualTo("CS-800");

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            Job savedNewJob = captor.getValue();
            assertThat(savedNewJob).isNotSameAs(existingLinkedinJob);
            assertThat(savedNewJob.getSource()).isEqualTo(JobSource.CUTSHORT);
            assertThat(savedNewJob.getExternalJobId()).isEqualTo("CS-800");
        }

        @Test
        @DisplayName("4. NOT_DUPLICATE: Standard new record creation")
        void notDuplicate_createsNewJobRecord() {
            when(jobRepository.findBySourceAndExternalJobId(JobSource.FOUNDIT, "FI-123"))
                    .thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://foundit.in/job/123"))
                    .thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("Golang Developer", "FastTech"))
                    .thenReturn(List.of());
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            JobIngestionCandidate candidate = createCandidate(
                    JobSource.FOUNDIT, "FI-123", "Golang Developer", "FastTech", "Berlin", "Go microservices", "https://foundit.in/job/123"
            );

            JobCandidateIngestionResult result = ingestionService.ingestCandidate(candidate);

            assertThat(result.action()).isEqualTo(CandidateIngestionAction.CREATED);
            assertThat(result.isDuplicate()).isFalse();
            assertThat(result.duplicateResult().classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
            assertThat(result.job().source()).isEqualTo(JobSource.FOUNDIT);
        }
    }

    @Nested
    @DisplayName("Orchestrator Metrics, Isolation & Resilience")
    class OrchestrationAndMetricsTests {

        private SimpleMeterRegistry meterRegistry;
        private JobIngestionMetrics metrics;
        private JobSourceRegistry sourceRegistry;
        private JobSourceControlPolicy controlPolicy;
        private JobIngestionOrchestrator orchestrator;

        private void setupOrchestrator(JobSourceAdapter adapter) {
            meterRegistry = new SimpleMeterRegistry();
            metrics = new JobIngestionMetrics(meterRegistry);
            com.joblivo.job.config.JobDiscoveryProperties properties = new com.joblivo.job.config.JobDiscoveryProperties();
            properties.setEnabled(true);
            properties.setEnabledSources(java.util.Set.of(adapter.source()));
            sourceRegistry = new JobSourceRegistry(List.of(adapter), properties);
            controlPolicy = new JobSourceControlPolicy(properties, sourceRegistry);
            orchestrator = new JobIngestionOrchestrator(
                    sourceRegistry,
                    controlPolicy,
                    ingestionService,
                    metrics
            );
        }

        @Test
        @DisplayName("5. Metrics: Records low-cardinality counter for duplicate detections")
        void recordsLowCardinalityDuplicateMetrics() {
            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    JobIngestionCandidate c1 = createCandidate(JobSource.LINKEDIN, "LI-1", "Eng", "Co", "Loc", "Desc", "https://example.com/1");
                    JobIngestionCandidate c2 = createCandidate(JobSource.LINKEDIN, "LI-2", "Dev", "Co", "Loc", "Desc", "https://example.com/2");
                    return List.of(c1, c2);
                }
            };
            setupOrchestrator(adapter);

            // c1 is an exact source duplicate
            Job stored1 = createStoredJob(JobSource.LINKEDIN, "LI-1", "Eng", "Co", "Loc", "Desc", "https://example.com/1");
            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "LI-1")).thenReturn(Optional.of(stored1));

            // c2 is not a duplicate
            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "LI-2")).thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/2")).thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("Dev", "Co")).thenReturn(List.of());
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            IngestionRunResult result = orchestrator.ingestSource(JobSource.LINKEDIN, JobFetchCriteria.of(null, null), "test-run");

            assertThat(result.updatedCount()).isEqualTo(1);
            assertThat(result.createdCount()).isEqualTo(1);
            assertThat(result.failedCount()).isEqualTo(0);

            // Verify metric
            Counter counter = meterRegistry.find(JobIngestionMetrics.METRIC_DUPLICATES_DETECTED)
                    .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                    .tag(JobIngestionMetrics.TAG_DUPLICATE_CLASSIFICATION, "EXACT_SOURCE_DUPLICATE")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("6. Failure isolation: Per-candidate failure isolation remains intact")
        void perCandidateFailureIsolationRemainsIntact() {
            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource getSource() {
                    return JobSource.NAUKRI;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    JobIngestionCandidate c1 = createCandidate(JobSource.NAUKRI, "NK-1", "Valid 1", "Co", "Loc", "Desc", "https://example.com/1");
                    JobIngestionCandidate c2 = createCandidate(JobSource.NAUKRI, "NK-2", "   ", "Co", "Loc", "Desc", "https://example.com/2"); // Invalid blank title
                    JobIngestionCandidate c3 = createCandidate(JobSource.NAUKRI, "NK-3", "Valid 3", "Co", "Loc", "Desc", "https://example.com/3");
                    return List.of(c1, c2, c3);
                }
            };
            setupOrchestrator(adapter);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, "NK-1")).thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/1")).thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("Valid 1", "Co")).thenReturn(List.of());

            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, "NK-3")).thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/3")).thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("Valid 3", "Co")).thenReturn(List.of());

            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            IngestionRunResult runResult = orchestrator.ingestSource(JobSource.NAUKRI);

            assertThat(runResult.createdCount()).isEqualTo(2);
            assertThat(runResult.failedCount()).isEqualTo(1);
            assertThat(runResult.failureSummaries()).hasSize(1);
            assertThat(runResult.failureSummaries().get(0).externalJobId()).isEqualTo("NK-2");
        }

        @Test
        @DisplayName("7. Concurrency: Database uniqueness constraint violation handled as DATA_INTEGRITY_ERROR")
        void databaseUniquenessConflict_handledAsDataIntegrityError() {
            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource getSource() {
                    return JobSource.CUTSHORT;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    return List.of(createCandidate(JobSource.CUTSHORT, "CS-1", "Dev", "Co", "Loc", "Desc", "https://example.com"));
                }
            };
            setupOrchestrator(adapter);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.CUTSHORT, "CS-1")).thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com")).thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("Dev", "Co")).thenReturn(List.of());
            when(jobRepository.save(any(Job.class))).thenThrow(new DataIntegrityViolationException("uq_jobs_source_external_id"));

            IngestionRunResult runResult = orchestrator.ingestSource(JobSource.CUTSHORT);

            assertThat(runResult.failedCount()).isEqualTo(1);
            assertThat(runResult.failureSummaries().get(0).failureCategory()).isEqualTo("DATA_INTEGRITY_ERROR");
        }

        @Test
        @DisplayName("8. Batch deduplication from Prompt 44 still skips duplicate in batch")
        void batchDeduplicationStillWorks() {
            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource getSource() {
                    return JobSource.INSTAHYRE;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    JobIngestionCandidate c1 = createCandidate(JobSource.INSTAHYRE, "IH-1", "Dev", "Co", "Loc", "Desc", "https://example.com/1");
                    JobIngestionCandidate c2 = createCandidate(JobSource.INSTAHYRE, "IH-1", "Dev", "Co", "Loc", "Desc", "https://example.com/1");
                    return List.of(c1, c2);
                }
            };
            setupOrchestrator(adapter);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.INSTAHYRE, "IH-1")).thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/1")).thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("Dev", "Co")).thenReturn(List.of());
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            IngestionRunResult runResult = orchestrator.ingestSource(JobSource.INSTAHYRE);

            assertThat(runResult.createdCount()).isEqualTo(1);
            assertThat(runResult.skippedCount()).isEqualTo(1);
        }
    }
}

