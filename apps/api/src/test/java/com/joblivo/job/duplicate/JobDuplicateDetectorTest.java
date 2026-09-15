package com.joblivo.job.duplicate;

import com.joblivo.job.Job;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobDuplicateDetector & JobContentFingerprint Unit Tests")
class JobDuplicateDetectorTest {

    private JobDuplicateDetector detector;

    @BeforeEach
    void setUp() {
        detector = new JobDuplicateDetector();
    }

    private static Job createSampleJob(
            UUID id,
            JobSource source,
            String externalJobId,
            String title,
            String companyName,
            String location,
            String description,
            String jobUrl
    ) {
        try {
            Job job = new Job(source, externalJobId, title, companyName);
            job.setLocation(location);
            job.setDescription(description);
            job.setJobUrl(jobUrl);
            job.setRecruiterName("Jane Recruiter");
            job.setCompanyUrl("https://company.com");
            job.setWorkMode(JobWorkMode.HYBRID);
            job.setEmploymentType(JobEmploymentType.FULL_TIME);
            job.setApplicationMethod(JobApplicationMethod.EXTERNAL_COMPANY_SITE);
            job.setExperienceMinYears(3);
            job.setExperienceMaxYears(6);
            job.setSalaryMin(BigDecimal.valueOf(120000));
            job.setSalaryMax(BigDecimal.valueOf(160000));
            job.setSalaryCurrency("USD");
            job.setSalaryPeriod(SalaryPeriod.YEAR);
            job.setPostedAt(Instant.now().minus(2, ChronoUnit.DAYS));
            job.setExpiresAt(Instant.now().plus(28, ChronoUnit.DAYS));
            job.setDiscoveredAt(Instant.now().minus(1, ChronoUnit.DAYS));
            job.setLastSeenAt(Instant.now());

            Field idField = Job.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(job, id);

            return job;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JobIngestionCandidate createCandidate(
            JobSource source,
            String externalJobId,
            String title,
            String companyName,
            String location,
            String description,
            String jobUrl
    ) {
        return JobIngestionCandidate.builder()
                .source(source)
                .externalJobId(externalJobId)
                .title(title)
                .companyName(companyName)
                .location(location)
                .description(description)
                .jobUrl(jobUrl)
                .workMode(JobWorkMode.REMOTE)
                .employmentType(JobEmploymentType.FULL_TIME)
                .build();
    }

    @Nested
    @DisplayName("Signal 1: Exact Source Identity Duplicate")
    class SourceIdentityDuplicateTests {

        @Test
        @DisplayName("1. Same source + same external job ID => EXACT_SOURCE_DUPLICATE")
        void sameSourceAndSameExternalId_IsSourceDuplicate() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-101",
                    "Java Engineer", "Acme Corp", "Austin, TX", "Write Java code.", "https://linkedin.com/jobs/101");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-101",
                    "Senior Java Engineer", "Acme Corp Inc", "Austin, TX", "Write senior Java code.", "https://linkedin.com/jobs/diff");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.EXACT_SOURCE_DUPLICATE);
            assertThat(result.reason()).isEqualTo("Same source and external job ID");
            assertThat(result.evidenceDetail()).isEqualTo("LINKEDIN:ext-101");
        }

        @Test
        @DisplayName("2. Same source + different external job ID => not an exact source duplicate")
        void sameSourceDifferentExternalId_NotSourceDuplicate() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-101",
                    "Java Engineer", "Acme Corp", "Austin, TX", "Desc A", "https://linkedin.com/jobs/101");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-102",
                    "Frontend Dev", "Beta Corp", "Seattle, WA", "Desc B", "https://linkedin.com/jobs/102");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
        }

        @Test
        @DisplayName("3. Different source + same external job ID => not an exact source duplicate")
        void differentSourceSameExternalId_NotSourceDuplicate() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "shared-ext-id-999",
                    "Java Engineer", "Acme Corp", "Austin, TX", "Desc A", "https://linkedin.com/jobs/999");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.NAUKRI, "shared-ext-id-999",
                    "Frontend Dev", "Beta Corp", "Seattle, WA", "Desc B", "https://naukri.com/jobs/999");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
        }

        @Test
        @DisplayName("Whitespace in external job ID is normalized before source comparison")
        void whitespaceInExternalIdNormalized() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "  job-777  ",
                    "Java Engineer", "Acme", "Remote", "Desc", "https://linkedin.com/jobs/777");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "job-777",
                    "Backend Engineer", "Beta", "Remote", "Desc", "https://linkedin.com/jobs/diff");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.EXACT_SOURCE_DUPLICATE);
        }
    }

    @Nested
    @DisplayName("Signal 2: Exact Canonical Job URL Duplicate")
    class CanonicalUrlDuplicateTests {

        @Test
        @DisplayName("4. Same canonical URL => EXACT_URL_DUPLICATE (across different sources)")
        void sameCanonicalUrl_IsUrlDuplicate() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-linkedin-1",
                    "Java Engineer", "Acme Corp", "Austin, TX", "Desc A", "https://careers.acme.com/jobs/view/456");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.ATS, "ext-greenhouse-99",
                    "Software Engineer", "Acme Inc", "Austin, TX", "Desc B", "https://careers.acme.com/jobs/view/456");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.EXACT_URL_DUPLICATE);
            assertThat(result.reason()).isEqualTo("Same canonical job URL");
            assertThat(result.evidenceDetail()).isEqualTo("https://careers.acme.com/jobs/view/456");
        }

        @Test
        @DisplayName("5. Different canonical URLs => URL evidence does not classify as duplicate")
        void differentCanonicalUrls_NotUrlDuplicate() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-1",
                    "Java Engineer", "Acme", "Remote", "Desc 1", "https://acme.com/jobs/1");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.NAUKRI, "ext-2",
                    "Frontend Dev", "Beta", "Remote", "Desc 2", "https://acme.com/jobs/2");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
        }

        @Test
        @DisplayName("URLs with surrounding whitespace are trimmed and recognized as exact URL duplicate")
        void urlWithWhitespaceIsRecognized() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-1",
                    "Java Engineer", "Acme", "Remote", "Desc 1", "  https://company.com/job/100  ");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.COMPANY_CAREERS, "ext-2",
                    "Backend Dev", "Beta", "Remote", "Desc 2", "https://company.com/job/100");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.EXACT_URL_DUPLICATE);
            assertThat(result.evidenceDetail()).isEqualTo("https://company.com/job/100");
        }

        @Test
        @DisplayName("Missing or malformed URLs do not produce false positive URL duplicate")
        void nullOrMalformedUrls_DoNotProduceUrlDuplicate() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-1",
                    "Java Engineer", "Acme", "Remote", "Desc 1", null);
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.COMPANY_CAREERS, "ext-2",
                    "Backend Dev", "Beta", "Remote", "Desc 2", null);

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
        }
    }

    @Nested
    @DisplayName("Signal 3: Exact Content Fingerprint")
    class ContentFingerprintTests {

        @Test
        @DisplayName("6. Same normalized title/company/location/description => EXACT_CONTENT_DUPLICATE")
        void sameContentAttributes_IsExactContentDuplicate() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-linkedin-500",
                    "Staff Cloud Architect", "FinTech Global", "New York, NY",
                    "Lead multi-cloud architecture and Terraform migration.", "https://linkedin.com/jobs/500");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.CUTSHORT, "ext-cutshort-800",
                    "Staff Cloud Architect", "FinTech Global", "New York, NY",
                    "Lead multi-cloud architecture and Terraform migration.", "https://cutshort.io/jobs/800");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.EXACT_CONTENT_DUPLICATE);
            assertThat(result.reason()).isEqualTo("Same canonical job content fingerprint");
            assertThat(result.evidenceDetail()).hasSize(64);
        }

        @Test
        @DisplayName("7. Differences only in irrelevant whitespace handled by normalization => same fingerprint")
        void irrelevantWhitespaceDifferences_ProduceSameFingerprint() {
            JobContentFingerprint fp1 = JobContentFingerprint.of(
                    "Senior   Java    Developer",
                    "Acme   Corp",
                    "Austin,   TX",
                    "We   are   hiring   developers."
            );

            JobContentFingerprint fp2 = JobContentFingerprint.of(
                    "Senior Java Developer",
                    "Acme Corp",
                    "Austin, TX",
                    "We are hiring developers."
            );

            assertThat(fp1).isEqualTo(fp2);
            assertThat(fp1.hashHex()).isEqualTo(fp2.hashHex());
        }

        @Test
        @DisplayName("8. Case differences handled by canonical normalization => same fingerprint")
        void caseDifferences_ProduceSameFingerprint() {
            JobContentFingerprint fpLower = JobContentFingerprint.of(
                    "senior java developer", "acme corp", "austin, tx", "build apis"
            );
            JobContentFingerprint fpUpper = JobContentFingerprint.of(
                    "SENIOR JAVA DEVELOPER", "ACME CORP", "AUSTIN, TX", "BUILD APIS"
            );
            JobContentFingerprint fpMixed = JobContentFingerprint.of(
                    "Senior Java Developer", "Acme Corp", "Austin, TX", "Build APIs"
            );

            assertThat(fpLower.hashHex()).isEqualTo(fpUpper.hashHex());
            assertThat(fpLower.hashHex()).isEqualTo(fpMixed.hashHex());
        }

        @Test
        @DisplayName("9. Meaningful description change => different fingerprint")
        void meaningfulDescriptionChange_ProducesDifferentFingerprint() {
            JobContentFingerprint fp1 = JobContentFingerprint.of(
                    "Java Engineer", "Acme", "Remote", "Designing Kafka event streams."
            );
            JobContentFingerprint fp2 = JobContentFingerprint.of(
                    "Java Engineer", "Acme", "Remote", "Building React frontends."
            );

            assertThat(fp1.hashHex()).isNotEqualTo(fp2.hashHex());
        }

        @Test
        @DisplayName("10. Different company => different content fingerprint")
        void differentCompany_ProducesDifferentFingerprint() {
            JobContentFingerprint fp1 = JobContentFingerprint.of(
                    "Java Engineer", "Acme Corp", "Remote", "Desc"
            );
            JobContentFingerprint fp2 = JobContentFingerprint.of(
                    "Java Engineer", "Beta Corp", "Remote", "Desc"
            );

            assertThat(fp1.hashHex()).isNotEqualTo(fp2.hashHex());
        }

        @Test
        @DisplayName("11. Different title => different content fingerprint")
        void differentTitle_ProducesDifferentFingerprint() {
            JobContentFingerprint fp1 = JobContentFingerprint.of(
                    "Junior Java Engineer", "Acme", "Remote", "Desc"
            );
            JobContentFingerprint fp2 = JobContentFingerprint.of(
                    "Senior Java Engineer", "Acme", "Remote", "Desc"
            );

            assertThat(fp1.hashHex()).isNotEqualTo(fp2.hashHex());
        }

        @Test
        @DisplayName("12. Different location => different content fingerprint")
        void differentLocation_ProducesDifferentFingerprint() {
            JobContentFingerprint fp1 = JobContentFingerprint.of(
                    "Java Engineer", "Acme", "Austin, TX", "Desc"
            );
            JobContentFingerprint fp2 = JobContentFingerprint.of(
                    "Java Engineer", "Acme", "Seattle, WA", "Desc"
            );

            assertThat(fp1.hashHex()).isNotEqualTo(fp2.hashHex());
        }

        @Test
        @DisplayName("13. Null and blank optional values are handled deterministically")
        void nullAndBlankOptionalValuesHandledDeterministically() {
            JobContentFingerprint fpNull = JobContentFingerprint.of(
                    "Java Engineer", "Acme", null, null
            );
            JobContentFingerprint fpBlank = JobContentFingerprint.of(
                    "Java Engineer", "Acme", "   ", ""
            );

            assertThat(fpNull).isEqualTo(fpBlank);
            assertThat(fpNull.hashHex()).isEqualTo(fpBlank.hashHex());
        }

        @Test
        @DisplayName("14. Field boundaries prevent ambiguous concatenation")
        void fieldBoundariesPreventAmbiguousConcatenation() {
            // "title=A, company=BC" vs "title=AB, company=C"
            JobContentFingerprint fp1 = JobContentFingerprint.of("A", "BC", "Remote", "Desc");
            JobContentFingerprint fp2 = JobContentFingerprint.of("AB", "C", "Remote", "Desc");

            assertThat(fp1.hashHex()).isNotEqualTo(fp2.hashHex());

            String ser1 = JobContentFingerprint.buildCanonicalSerialization("A", "BC", "Remote", "Desc");
            String ser2 = JobContentFingerprint.buildCanonicalSerialization("AB", "C", "Remote", "Desc");
            assertThat(ser1).isNotEqualTo(ser2);
        }

        @Test
        @DisplayName("15. SHA-256 output is deterministic and 64 lowercase hex characters")
        void sha256OutputIsDeterministicAnd64Hex() {
            JobContentFingerprint fp1 = JobContentFingerprint.of(
                    "Principal Architect", "Global Systems", "Bengaluru", "Cloud Architecture"
            );
            JobContentFingerprint fp2 = JobContentFingerprint.of(
                    "Principal Architect", "Global Systems", "Bengaluru", "Cloud Architecture"
            );

            assertThat(fp1.hashHex()).matches("^[0-9a-f]{64}$");
            assertThat(fp1.hashHex()).isEqualTo(fp2.hashHex());
        }
    }

    @Nested
    @DisplayName("Precedence & Priority Rules")
    class PrecedenceAndPriorityTests {

        @Test
        @DisplayName("16. Source identity takes precedence over URL and content evidence")
        void sourceIdentityTakesPrecedenceOverUrlAndContent() {
            // Same source+id, same URL, same content -> Priority 1 (EXACT_SOURCE_DUPLICATE)
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-100",
                    "Lead Developer", "Acme", "Remote", "Desc", "https://linkedin.com/jobs/100");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-100",
                    "Lead Developer", "Acme", "Remote", "Desc", "https://linkedin.com/jobs/100");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.EXACT_SOURCE_DUPLICATE);
        }

        @Test
        @DisplayName("17. URL evidence takes precedence over content evidence")
        void urlEvidenceTakesPrecedenceOverContentEvidence() {
            // Different sources, same URL, same content -> Priority 2 (EXACT_URL_DUPLICATE)
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-1",
                    "Lead Developer", "Acme", "Remote", "Desc", "https://company.com/job/999");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.COMPANY_CAREERS, "ext-2",
                    "Lead Developer", "Acme", "Remote", "Desc", "https://company.com/job/999");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.EXACT_URL_DUPLICATE);
        }

        @Test
        @DisplayName("18. NOT_DUPLICATE is returned when no deterministic duplicate evidence exists")
        void notDuplicateReturnedWhenNoSignalsMatch() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-1",
                    "Data Scientist", "Alpha", "Chicago", "Python ML", "https://alpha.com/job1");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.NAUKRI, "ext-2",
                    "Frontend Engineer", "Beta", "Denver", "React CSS", "https://beta.com/job2");

            JobDuplicateResult result = detector.compare(job1, job2);

            assertThat(result.isDuplicate()).isFalse();
            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
            assertThat(result.reason()).isEqualTo("No deterministic duplicate evidence found");
            assertThat(result.evidenceDetail()).isNull();
        }
    }

    @Nested
    @DisplayName("Safety, Immutability & Interoperability")
    class SafetyAndImmutabilityTests {

        @Test
        @DisplayName("19. Detector does not mutate input objects")
        void detectorDoesNotMutateInputObjects() {
            Job job1 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-1",
                    "Java Engineer", "Acme Corp", "Austin, TX", "Desc", "https://acme.com/jobs/1");
            Job job2 = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-1",
                    "Java Engineer", "Acme Corp", "Austin, TX", "Desc", "https://acme.com/jobs/1");

            String title1Before = job1.getTitle();
            Instant postedAt1Before = job1.getPostedAt();
            String comp2Before = job2.getCompanyName();

            detector.compare(job1, job2);

            assertThat(job1.getTitle()).isEqualTo(title1Before);
            assertThat(job1.getPostedAt()).isEqualTo(postedAt1Before);
            assertThat(job2.getCompanyName()).isEqualTo(comp2Before);
        }

        @Test
        @DisplayName("20. Overloaded compare supports JobIngestionCandidate interoperability")
        void overloadedCompareSupportsCandidateInteroperability() {
            Job existingJob = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-100",
                    "Java Engineer", "Acme", "Remote", "Desc", "https://acme.com/jobs/1");
            JobIngestionCandidate candidate = createCandidate(JobSource.LINKEDIN, "ext-100",
                    "Java Engineer", "Acme", "Remote", "Desc", "https://acme.com/jobs/1");

            JobDuplicateResult result = detector.compare(existingJob, candidate);

            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.EXACT_SOURCE_DUPLICATE);

            JobIngestionCandidate c1 = createCandidate(JobSource.ATS, "ext-a",
                    "Dev", "Corp", "Remote", "Desc", "https://corp.com/job");
            JobIngestionCandidate c2 = createCandidate(JobSource.COMPANY_CAREERS, "ext-b",
                    "Dev", "Corp", "Remote", "Desc", "https://corp.com/job");

            JobDuplicateResult candidateResult = detector.compare(c1, c2);
            assertThat(candidateResult.classification()).isEqualTo(JobDuplicateClassification.EXACT_URL_DUPLICATE);
        }

        @Test
        @DisplayName("Null arguments throw NullPointerException fail-fast")
        void nullArgumentsThrow() {
            Job job = createSampleJob(UUID.randomUUID(), JobSource.LINKEDIN, "ext-1",
                    "Title", "Comp", "Loc", "Desc", "https://acme.com");

            assertThatThrownBy(() -> detector.compare(null, job))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("job1 must not be null");

            assertThatThrownBy(() -> detector.compare(job, (Job) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("job2 must not be null");
        }
    }
}
