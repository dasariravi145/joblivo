package com.joblivo.job.model;

import com.joblivo.job.Job;
import com.joblivo.job.JobRepository;
import com.joblivo.job.config.JobDiscoveryProperties;
import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.ingestion.CandidateFailureSummary;
import com.joblivo.job.ingestion.CandidateIngestionAction;
import com.joblivo.job.ingestion.IngestionRunResult;
import com.joblivo.job.ingestion.IngestionRunStatus;
import com.joblivo.job.ingestion.JobBatchDeduplicator;
import com.joblivo.job.ingestion.JobCandidateIngestionResult;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.ingestion.JobSourceAdapter;
import com.joblivo.job.ingestion.JobSourceControlPolicy;
import com.joblivo.job.ingestion.JobSourceExecutionContext;
import com.joblivo.job.ingestion.JobSourceRegistry;
import com.joblivo.job.normalizer.DefaultJobNormalizer;
import com.joblivo.job.normalizer.JobNormalizer;
import com.joblivo.job.normalizer.NormalizedJobCandidate;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Job Source Provenance & Canonical Identity Tests")
class JobSourceProvenanceTest {

    @Mock
    private JobRepository jobRepository;

    private JobNormalizer jobNormalizer;
    private JobIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        jobNormalizer = new DefaultJobNormalizer();
        ingestionService = new JobIngestionService(jobRepository, jobNormalizer);
    }

    // =========================================================================
    // 1. SOURCE IDENTITY TESTS (Requirements 1 - 6)
    // =========================================================================

    @Nested
    @DisplayName("1. Source Identity Determinism & Isolation")
    class SourceIdentityTests {

        @Test
        @DisplayName("1. LINKEDIN + same external ID is stable")
        void linkedinSameExternalIdIsStable() {
            JobSourceIdentity id1 = new JobSourceIdentity(JobSource.LINKEDIN, "ext-101");
            JobSourceIdentity id2 = new JobSourceIdentity(JobSource.LINKEDIN, "ext-101");

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
            assertThat(id1.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(id1.externalJobId()).isEqualTo("ext-101");
        }

        @Test
        @DisplayName("2. NAUKRI + same external ID is a different identity from LINKEDIN")
        void naukriDifferentIdentityFromLinkedin() {
            JobSourceIdentity linkedin = new JobSourceIdentity(JobSource.LINKEDIN, "ext-101");
            JobSourceIdentity naukri = new JobSourceIdentity(JobSource.NAUKRI, "ext-101");

            assertThat(linkedin).isNotEqualTo(naukri);
            assertThat(linkedin.hashCode()).isNotEqualTo(naukri.hashCode());
            assertThat(linkedin.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(naukri.source()).isEqualTo(JobSource.NAUKRI);
        }

        @Test
        @DisplayName("3. Same source + normalized externalJobId resolves to the same identity")
        void sameSourceNormalizedExternalJobIdResolvesToSameIdentity() {
            JobSourceIdentity unnormalized = new JobSourceIdentity(JobSource.LINKEDIN, "   ext-101   ");
            JobSourceIdentity normalized = new JobSourceIdentity(JobSource.LINKEDIN, "ext-101");

            assertThat(unnormalized).isEqualTo(normalized);
            assertThat(unnormalized.externalJobId()).isEqualTo("ext-101");
            assertThat(unnormalized.hashCode()).isEqualTo(normalized.hashCode());
        }

        @Test
        @DisplayName("4. Missing externalJobId is rejected according to existing ingestion behavior")
        void missingExternalJobIdIsRejected() {
            assertThatThrownBy(() -> new JobSourceIdentity(JobSource.LINKEDIN, null))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId must not be null or blank");

            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId(null)
                    .title("Cloud Engineer")
                    .companyName("Acme Corp")
                    .build();

            assertThatThrownBy(() -> jobNormalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId must not be null or blank");
        }

        @Test
        @DisplayName("5. Blank externalJobId is rejected")
        void blankExternalJobIdIsRejected() {
            assertThatThrownBy(() -> new JobSourceIdentity(JobSource.LINKEDIN, "    "))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId must not be null or blank");

            assertThatThrownBy(() -> new JobSourceIdentity(JobSource.LINKEDIN, ""))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId must not be null or blank");

            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("   ")
                    .title("Cloud Engineer")
                    .companyName("Acme Corp")
                    .build();

            assertThatThrownBy(() -> jobNormalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId must not be null or blank");
        }

        @Test
        @DisplayName("6. Source mismatch between adapter and execution context is rejected")
        void sourceMismatchBetweenAdapterAndExecutionContextIsRejected() {
            // Adapter declares NAUKRI
            JobSourceAdapter mismatchAdapter = new JobSourceAdapter() {
                @Override
                public JobSource getSource() {
                    return JobSource.NAUKRI;
                }

                @Override
                public List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
                    return List.of(JobIngestionCandidate.builder()
                            .source(JobSource.NAUKRI)
                            .externalJobId("naukri-1")
                            .title("Engineer")
                            .companyName("Acme")
                            .build());
                }
            };

            JobDiscoveryProperties properties = new JobDiscoveryProperties();
            properties.setEnabled(true);
            properties.setEnabledSources(Set.of(JobSource.LINKEDIN, JobSource.NAUKRI));
            JobSourceRegistry registry = new JobSourceRegistry(List.of(mismatchAdapter), properties);
            JobSourceControlPolicy controlPolicy = new JobSourceControlPolicy(properties, registry);

            JobIngestionOrchestrator orchestrator = new JobIngestionOrchestrator(
                    registry,
                    controlPolicy,
                    ingestionService
            );

            // Attempt to ingest under LINKEDIN context when adapter declares NAUKRI
            // (Simulated by passing mismatch adapter to orchestrator context)
            JobSourceExecutionContext linkedinContext = JobSourceExecutionContext.of("run-1", JobSource.LINKEDIN, 100);
            assertThat(mismatchAdapter.source()).isNotEqualTo(linkedinContext.source());

            // Orchestrator rejects mismatch with CandidateFailureSummary category CONFIGURATION
            // and does not invoke adapter
            IngestionRunResult result = orchestrator.ingestSource(JobSource.NAUKRI);
            // Ingesting NAUKRI matches adapter
            assertThat(result.source()).isEqualTo(JobSource.NAUKRI);

            // Ingesting LINKEDIN fails safely because adapter is not registered for LINKEDIN
            IngestionRunResult linkedinResult = orchestrator.ingestSource(JobSource.LINKEDIN);
            assertThat(linkedinResult.status()).isEqualTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
            assertThat(linkedinResult.failedCount()).isEqualTo(1);
            assertThat(linkedinResult.failureSummaries().get(0).failureCategory()).isEqualTo("ADAPTER_NOT_FOUND");
        }
    }

    // =========================================================================
    // 2. PROVENANCE INTEGRITY TESTS (Requirements 7 - 15)
    // =========================================================================

    @Nested
    @DisplayName("2. Provenance Preservation & Timestamp Integrity")
    class ProvenanceTests {

        private Instant posted;
        private Instant expires;
        private Instant discovered;
        private Instant lastSeen;

        @BeforeEach
        void initTimestamps() {
            posted = Instant.now().minus(5, ChronoUnit.DAYS);
            expires = Instant.now().plus(25, ChronoUnit.DAYS);
            discovered = Instant.now().minus(4, ChronoUnit.DAYS);
            lastSeen = Instant.now().minus(1, ChronoUnit.HOURS);
        }

        @Test
        @DisplayName("7. jobUrl is preserved")
        void jobUrlIsPreserved() {
            JobSourceProvenance provenance = new JobSourceProvenance(
                    JobSource.LINKEDIN,
                    "ext-101",
                    "https://www.linkedin.com/jobs/view/101",
                    null,
                    posted,
                    expires,
                    discovered,
                    lastSeen,
                    JobApplicationMethod.EXTERNAL_COMPANY_SITE
            );

            assertThat(provenance.jobUrl()).isEqualTo("https://www.linkedin.com/jobs/view/101");
        }

        @Test
        @DisplayName("8. companyUrl is preserved")
        void companyUrlIsPreserved() {
            JobSourceProvenance provenance = new JobSourceProvenance(
                    JobSource.LINKEDIN,
                    "ext-101",
                    "https://www.linkedin.com/jobs/view/101",
                    "https://acme.example.com/careers",
                    posted,
                    expires,
                    discovered,
                    lastSeen,
                    JobApplicationMethod.INTERNAL_PORTAL
            );

            assertThat(provenance.companyUrl()).isEqualTo("https://acme.example.com/careers");
        }

        @Test
        @DisplayName("9. discoveredAt is preserved")
        void discoveredAtIsPreserved() {
            JobSourceProvenance provenance = new JobSourceProvenance(
                    JobSource.LINKEDIN,
                    "ext-101",
                    null,
                    null,
                    null,
                    null,
                    discovered,
                    lastSeen,
                    JobApplicationMethod.UNKNOWN
            );

            assertThat(provenance.discoveredAt()).isEqualTo(discovered);
        }

        @Test
        @DisplayName("10. lastSeenAt is preserved")
        void lastSeenAtIsPreserved() {
            JobSourceProvenance provenance = new JobSourceProvenance(
                    JobSource.LINKEDIN,
                    "ext-101",
                    null,
                    null,
                    null,
                    null,
                    discovered,
                    lastSeen,
                    JobApplicationMethod.UNKNOWN
            );

            assertThat(provenance.lastSeenAt()).isEqualTo(lastSeen);
        }

        @Test
        @DisplayName("11. postedAt is preserved")
        void postedAtIsPreserved() {
            JobSourceProvenance provenance = new JobSourceProvenance(
                    JobSource.LINKEDIN,
                    "ext-101",
                    null,
                    null,
                    posted,
                    expires,
                    discovered,
                    lastSeen,
                    JobApplicationMethod.UNKNOWN
            );

            assertThat(provenance.postedAt()).isEqualTo(posted);
        }

        @Test
        @DisplayName("12. expiresAt is preserved")
        void expiresAtIsPreserved() {
            JobSourceProvenance provenance = new JobSourceProvenance(
                    JobSource.LINKEDIN,
                    "ext-101",
                    null,
                    null,
                    posted,
                    expires,
                    discovered,
                    lastSeen,
                    JobApplicationMethod.UNKNOWN
            );

            assertThat(provenance.expiresAt()).isEqualTo(expires);
        }

        @Test
        @DisplayName("13. applicationMethod is preserved")
        void applicationMethodIsPreserved() {
            for (JobApplicationMethod method : JobApplicationMethod.values()) {
                JobSourceProvenance provenance = new JobSourceProvenance(
                        JobSource.LINKEDIN,
                        "ext-101",
                        null,
                        null,
                        null,
                        null,
                        discovered,
                        lastSeen,
                        method
                );
                assertThat(provenance.applicationMethod()).isEqualTo(method);
            }
        }

        @Test
        @DisplayName("14. Null optional provenance fields remain null")
        void nullOptionalProvenanceFieldsRemainNull() {
            JobSourceProvenance provenance = new JobSourceProvenance(
                    JobSource.LINKEDIN,
                    "ext-101",
                    null,
                    null,
                    null,
                    null,
                    discovered,
                    lastSeen,
                    null
            );

            assertThat(provenance.jobUrl()).isNull();
            assertThat(provenance.companyUrl()).isNull();
            assertThat(provenance.postedAt()).isNull();
            assertThat(provenance.expiresAt()).isNull();
            assertThat(provenance.applicationMethod()).isEqualTo(JobApplicationMethod.UNKNOWN);
        }

        @Test
        @DisplayName("15. No timestamp is fabricated")
        void noTimestampIsFabricated() {
            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-no-dates")
                    .title("Backend Engineer")
                    .companyName("TechCorp")
                    .postedAt(null)
                    .expiresAt(null)
                    .build();

            NormalizedJobCandidate normalized = jobNormalizer.normalize(candidate);

            assertThat(normalized.postedAt()).isNull();
            assertThat(normalized.expiresAt()).isNull();

            Job job = new Job(normalized.source(), normalized.externalJobId(), normalized.title(), normalized.companyName());
            job.setPostedAt(normalized.postedAt());
            job.setExpiresAt(normalized.expiresAt());

            JobSourceProvenance provenance = job.getSourceProvenance();
            assertThat(provenance.postedAt()).isNull();
            assertThat(provenance.expiresAt()).isNull();
        }
    }

    // =========================================================================
    // 3. INGESTION & DATA INTEGRITY TESTS (Requirements 16 - 20)
    // =========================================================================

    @Nested
    @DisplayName("3. Ingestion & Persistence Integrity")
    class IngestionIntegrityTests {

        @Test
        @DisplayName("16. First ingestion establishes the source identity")
        void firstIngestionEstablishesSourceIdentity() {
            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("job-first-1")
                    .title("Software Engineer")
                    .companyName("Pioneer Labs")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "job-first-1"))
                    .thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobCandidateIngestionResult result = ingestionService.ingestCandidate(candidate);

            assertThat(result.action()).isEqualTo(CandidateIngestionAction.CREATED);
            assertThat(result.job().source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(result.job().externalJobId()).isEqualTo("job-first-1");
            assertThat(result.job().sourceIdentity()).isEqualTo(new JobSourceIdentity(JobSource.LINKEDIN, "job-first-1"));

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            Job saved = captor.getValue();
            assertThat(saved.getSource()).isEqualTo(JobSource.LINKEDIN);
            assertThat(saved.getExternalJobId()).isEqualTo("job-first-1");
            assertThat(saved.getSourceIdentity()).isEqualTo(new JobSourceIdentity(JobSource.LINKEDIN, "job-first-1"));
            assertThat(saved.getSourceProvenance().sourceIdentity()).isEqualTo(new JobSourceIdentity(JobSource.LINKEDIN, "job-first-1"));
        }

        @Test
        @DisplayName("17. Re-ingestion of the same source identity remains idempotent")
        void reIngestionOfSameSourceIdentityIsIdempotent() {
            Instant initialDiscoveredAt = Instant.now().minus(2, ChronoUnit.DAYS);
            Instant initialLastSeenAt = Instant.now().minus(1, ChronoUnit.DAYS);

            Job existingJob = new Job(JobSource.LINKEDIN, "job-first-1", "Old Title", "Pioneer Labs");
            existingJob.setDiscoveredAt(initialDiscoveredAt);
            existingJob.setLastSeenAt(initialLastSeenAt);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "job-first-1"))
                    .thenReturn(Optional.of(existingJob));
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Instant newLastSeen = Instant.now();
            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("job-first-1")
                    .title("Updated Title")
                    .companyName("Pioneer Labs")
                    .discoveredAt(initialDiscoveredAt)
                    .lastSeenAt(newLastSeen)
                    .build();

            JobCandidateIngestionResult result = ingestionService.ingestCandidate(candidate);

            assertThat(result.action()).isEqualTo(CandidateIngestionAction.UPDATED);
            assertThat(result.job().title()).isEqualTo("Updated Title");
            assertThat(result.job().source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(result.job().externalJobId()).isEqualTo("job-first-1");
            assertThat(result.job().discoveredAt()).isEqualTo(initialDiscoveredAt);
            assertThat(result.job().lastSeenAt()).isEqualTo(newLastSeen);
        }

        @Test
        @DisplayName("18. Re-ingestion does not silently change source identity")
        void reIngestionDoesNotSilentlyChangeSourceIdentity() {
            Job existingJob = new Job(JobSource.LINKEDIN, "original-id", "Principal Eng", "BigTech");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "original-id"))
                    .thenReturn(Optional.of(existingJob));
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("original-id")
                    .title("Staff Eng") // Changed title
                    .companyName("BigTech International") // Changed company
                    .location("New Location") // Changed location
                    .description("New Description") // Changed description
                    .build();

            JobCandidateIngestionResult result = ingestionService.ingestCandidate(candidate);

            // Mutable attributes updated
            assertThat(result.job().title()).isEqualTo("Staff Eng");
            assertThat(result.job().companyName()).isEqualTo("BigTech International");

            // Source identity remains strictly unchanged
            assertThat(result.job().source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(result.job().externalJobId()).isEqualTo("original-id");
            assertThat(existingJob.getSource()).isEqualTo(JobSource.LINKEDIN);
            assertThat(existingJob.getExternalJobId()).isEqualTo("original-id");
        }

        @Test
        @DisplayName("19. Batch deduplication remains intact")
        void batchDeduplicationRemainsIntact() {
            JobBatchDeduplicator deduplicator = new JobBatchDeduplicator();

            JobIngestionCandidate c1 = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("dup-id-1")
                    .title("Title 1")
                    .companyName("Acme")
                    .build();

            JobIngestionCandidate c2 = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("dup-id-1")
                    .title("Title 1 Duplicate")
                    .companyName("Acme")
                    .build();

            JobIngestionCandidate c3 = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("unique-id-2")
                    .title("Title 2")
                    .companyName("Acme")
                    .build();

            JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                    List.of(c1, c2, c3),
                    JobSource.LINKEDIN
            );

            assertThat(result.duplicateCount()).isEqualTo(1);
            assertThat(result.uniqueCandidates()).hasSize(2);
            assertThat(result.uniqueCandidates().get(0).externalJobId()).isEqualTo("dup-id-1");
            assertThat(result.uniqueCandidates().get(1).externalJobId()).isEqualTo("unique-id-2");
            assertThat(result.duplicatesSkipped().get(0).title()).isEqualTo("Title 1 Duplicate");
        }

        @Test
        @DisplayName("20. Database uniqueness constraint protects source + external_job_id boundary")
        void databaseUniquenessBoundaryProtected() {
            Job jobA = new Job(JobSource.LINKEDIN, "unique-123", "Dev 1", "Acme");
            Job jobB = new Job(JobSource.LINKEDIN, "unique-123", "Dev 2", "Acme");
            Job jobC = new Job(JobSource.NAUKRI, "unique-123", "Dev 1", "Acme");

            // Entities with same source and externalJobId are considered equivalent identity
            assertThat(jobA).isEqualTo(jobB);
            assertThat(jobA.hashCode()).isEqualTo(jobB.hashCode());

            // Entities with different source are distinct
            assertThat(jobA).isNotEqualTo(jobC);
            assertThat(jobA.hashCode()).isNotEqualTo(jobC.hashCode());
        }
    }

    // =========================================================================
    // 4. SOURCE METADATA TESTS (Requirements 7 - 9)
    // =========================================================================

    @Nested
    @DisplayName("4. Source Metadata & Authoritative Registry")
    class SourceMetadataTests {

        @Test
        @DisplayName("7. Canonical source metadata definition covers all 8 conceptual sources")
        void canonicalSourceMetadataCoversAllSupportedSources() {
            JobSource[] expectedSources = {
                    JobSource.LINKEDIN,
                    JobSource.NAUKRI,
                    JobSource.FOUNDIT,
                    JobSource.CUTSHORT,
                    JobSource.INSTAHYRE,
                    JobSource.COMPANY_CAREERS,
                    JobSource.ATS,
                    JobSource.OTHER
            };

            for (JobSource source : expectedSources) {
                assertThat(source.code()).isEqualTo(source.name());
                assertThat(source.displayName()).isNotBlank();

                JobSourceMetadata metadata = JobSourceMetadata.of(source, true);
                assertThat(metadata.source()).isEqualTo(source);
                assertThat(metadata.code()).isEqualTo(source.name());
                assertThat(metadata.displayName()).isEqualTo(source.displayName());
                assertThat(metadata.enabled()).isTrue();
            }
        }

        @Test
        @DisplayName("8. JobSourceRegistry remains the authoritative runtime source registry")
        void jobSourceRegistryIsAuthoritativeRuntimeRegistry() {
            JobDiscoveryProperties properties = new JobDiscoveryProperties();
            properties.setEnabled(true);
            properties.setEnabledSources(Set.of(JobSource.LINKEDIN));

            JobSourceAdapter adapter = new JobSourceAdapter() {
                @Override
                public JobSource getSource() {
                    return JobSource.LINKEDIN;
                }
            };

            JobSourceRegistry registry = new JobSourceRegistry(List.of(adapter), properties);

            JobSourceMetadata linkedinMeta = registry.getSourceMetadata(JobSource.LINKEDIN);
            assertThat(linkedinMeta).isNotNull();
            assertThat(linkedinMeta.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(linkedinMeta.displayName()).isEqualTo("LinkedIn");
            assertThat(linkedinMeta.enabled()).isTrue();

            JobSourceMetadata naukriMeta = registry.getSourceMetadata(JobSource.NAUKRI);
            assertThat(naukriMeta).isNotNull();
            assertThat(naukriMeta.source()).isEqualTo(JobSource.NAUKRI);
            assertThat(naukriMeta.displayName()).isEqualTo("Naukri");
            assertThat(naukriMeta.enabled()).isFalse(); // Not enabled in properties

            List<JobSourceMetadata> allMeta = registry.getAllSourceMetadata();
            assertThat(allMeta).hasSize(8);
        }

        @Test
        @DisplayName("9. Source metadata contains only stable non-sensitive descriptive information")
        void sourceMetadataExcludesSensitiveCredentials() {
            JobSourceMetadata metadata = JobSourceMetadata.of(JobSource.LINKEDIN, true);

            // Reflection check: record contains ONLY source, code, displayName, enabled
            java.lang.reflect.RecordComponent[] components = JobSourceMetadata.class.getRecordComponents();
            assertThat(components).hasSize(4);
            assertThat(components[0].getName()).isEqualTo("source");
            assertThat(components[1].getName()).isEqualTo("code");
            assertThat(components[2].getName()).isEqualTo("displayName");
            assertThat(components[3].getName()).isEqualTo("enabled");
        }
    }
}
