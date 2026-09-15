package com.joblivo.job.duplicate;

import com.joblivo.job.Job;
import com.joblivo.job.JobRepository;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.normalizer.DefaultJobNormalizer;
import com.joblivo.job.normalizer.NormalizedJobCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobDuplicateDetector Stored Job Lookup Tests")
class JobStoredDuplicateDetectorTest {

    @Mock
    private JobRepository jobRepository;

    private JobDuplicateDetector detector;
    private final DefaultJobNormalizer normalizer = new DefaultJobNormalizer();

    @BeforeEach
    void setUp() {
        detector = new JobDuplicateDetector(jobRepository);
    }

    private Job createStoredJob(JobSource source, String externalId, String title, String company, String location, String description, String url) {
        Job job = new Job(source, externalId, title, company);
        job.setLocation(location);
        job.setDescription(description);
        job.setJobUrl(url);
        return job;
    }

    private NormalizedJobCandidate createCandidate(
            JobSource source, String externalId, String title, String company, String location, String description, String url
    ) {
        com.joblivo.job.ingestion.JobIngestionCandidate raw = com.joblivo.job.ingestion.JobIngestionCandidate.builder()
                .source(source)
                .externalJobId(externalId)
                .title(title)
                .companyName(company)
                .location(location)
                .description(description)
                .jobUrl(url)
                .build();
        return normalizer.normalize(raw);
    }

    @Nested
    @DisplayName("Signal Priority & Classification Tests")
    class SignalPriorityTests {

        @Test
        @DisplayName("1. Same source + externalJobId detects EXACT_SOURCE_DUPLICATE")
        void sameSourceAndExternalId_detectsExactSourceDuplicate() {
            Job stored = createStoredJob(JobSource.LINKEDIN, "LI-1001", "Software Engineer", "Acme", "Remote", "Desc", "https://example.com/li/1");
            NormalizedJobCandidate candidate = createCandidate(JobSource.LINKEDIN, "LI-1001", "Senior Engineer", "Acme Corp", "Hybrid", "Updated desc", "https://example.com/other");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "LI-1001"))
                    .thenReturn(Optional.of(stored));

            StoredJobDuplicateMatch match = detector.findStoredDuplicate(candidate);

            assertThat(match.isDuplicate()).isTrue();
            assertThat(match.classification()).isEqualTo(JobDuplicateClassification.EXACT_SOURCE_DUPLICATE);
            assertThat(match.matchedJob()).isSameAs(stored);
            assertThat(match.result().evidenceDetail()).isEqualTo("LINKEDIN:LI-1001");
            verify(jobRepository, never()).findFirstByJobUrl(any());
        }

        @Test
        @DisplayName("2. Same canonical URL with different source identity detects EXACT_URL_DUPLICATE")
        void sameCanonicalUrlDifferentSource_detectsExactUrlDuplicate() {
            Job stored = createStoredJob(JobSource.LINKEDIN, "LI-1001", "Backend Dev", "Acme", "SF", "Desc", "https://example.com/jobs/dev-1");
            NormalizedJobCandidate candidate = createCandidate(JobSource.NAUKRI, "NK-2002", "Backend Dev", "Acme", "SF", "Desc", "https://example.com/jobs/dev-1");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, "NK-2002"))
                    .thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/jobs/dev-1"))
                    .thenReturn(Optional.of(stored));

            StoredJobDuplicateMatch match = detector.findStoredDuplicate(candidate);

            assertThat(match.isDuplicate()).isTrue();
            assertThat(match.classification()).isEqualTo(JobDuplicateClassification.EXACT_URL_DUPLICATE);
            assertThat(match.matchedJob()).isSameAs(stored);
            assertThat(match.result().evidenceDetail()).isEqualTo("https://example.com/jobs/dev-1");
        }

        @Test
        @DisplayName("3. Same content fingerprint with different source identity detects EXACT_CONTENT_DUPLICATE")
        void sameContentFingerprint_detectsExactContentDuplicate() {
            Job stored = createStoredJob(JobSource.LINKEDIN, "LI-1001", "Staff Java Architect", "Acme Corp", "San Francisco", "Lead core platform", "https://example.com/li");
            NormalizedJobCandidate candidate = createCandidate(JobSource.CUTSHORT, "CS-3003", "Staff   Java Architect", "Acme Corp ", "san francisco", "Lead core platform", "https://example.com/cs");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.CUTSHORT, "CS-3003"))
                    .thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/cs"))
                    .thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("Staff Java Architect", "Acme Corp"))
                    .thenReturn(List.of(stored));

            StoredJobDuplicateMatch match = detector.findStoredDuplicate(candidate);

            assertThat(match.isDuplicate()).isTrue();
            assertThat(match.classification()).isEqualTo(JobDuplicateClassification.EXACT_CONTENT_DUPLICATE);
            assertThat(match.matchedJob()).isSameAs(stored);
            assertThat(match.result().evidenceDetail()).isEqualTo(JobContentFingerprint.fromJob(stored).hashHex());
        }

        @Test
        @DisplayName("4. Completely different job returns NOT_DUPLICATE")
        void completelyDifferentJob_returnsNotDuplicate() {
            NormalizedJobCandidate candidate = createCandidate(JobSource.FOUNDIT, "FI-9999", "DevOps Engineer", "Cloud Solutions", "Austin", "Manage AWS", "https://example.com/fi/1");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.FOUNDIT, "FI-9999")).thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/fi/1")).thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("DevOps Engineer", "Cloud Solutions")).thenReturn(List.of());

            StoredJobDuplicateMatch match = detector.findStoredDuplicate(candidate);

            assertThat(match.isDuplicate()).isFalse();
            assertThat(match.classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
            assertThat(match.matchedJob()).isNull();
        }

        @Test
        @DisplayName("5. Different source + different external ID + different URL does not become duplicate")
        void differentSourceAndIdAndUrl_doesNotBecomeDuplicate() {
            Job stored = createStoredJob(JobSource.LINKEDIN, "LI-1", "Frontend Dev", "Tech Corp", "NY", "React dev", "https://example.com/li/1");
            NormalizedJobCandidate candidate = createCandidate(JobSource.NAUKRI, "NK-2", "Backend Dev", "Tech Corp", "NY", "Java dev", "https://example.com/nk/2");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, "NK-2")).thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/nk/2")).thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("Backend Dev", "Tech Corp")).thenReturn(List.of(stored));

            StoredJobDuplicateMatch match = detector.findStoredDuplicate(candidate);

            assertThat(match.isDuplicate()).isFalse();
            assertThat(match.classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
        }

        @Test
        @DisplayName("6. Same source + external ID takes precedence over URL and content evidence")
        void sourceIdentityTakesPrecedenceOverUrlAndContent() {
            Job stored = createStoredJob(JobSource.LINKEDIN, "LI-100", "Lead Architect", "Acme", "London", "Lead team", "https://example.com/job/100");
            NormalizedJobCandidate candidate = createCandidate(JobSource.LINKEDIN, "LI-100", "Lead Architect", "Acme", "London", "Lead team", "https://example.com/job/100");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "LI-100")).thenReturn(Optional.of(stored));

            StoredJobDuplicateMatch match = detector.findStoredDuplicate(candidate);

            assertThat(match.classification()).isEqualTo(JobDuplicateClassification.EXACT_SOURCE_DUPLICATE);
            verify(jobRepository, never()).findFirstByJobUrl(any());
        }

        @Test
        @DisplayName("7. Same URL takes precedence over content evidence")
        void urlTakesPrecedenceOverContent() {
            Job stored = createStoredJob(JobSource.LINKEDIN, "LI-100", "Data Scientist", "Analytics Inc", "Remote", "ML models", "https://example.com/ds/1");
            NormalizedJobCandidate candidate = createCandidate(JobSource.INSTAHYRE, "IH-200", "Data Scientist", "Analytics Inc", "Remote", "ML models", "https://example.com/ds/1");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.INSTAHYRE, "IH-200")).thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/ds/1")).thenReturn(Optional.of(stored));

            StoredJobDuplicateMatch match = detector.findStoredDuplicate(candidate);

            assertThat(match.classification()).isEqualTo(JobDuplicateClassification.EXACT_URL_DUPLICATE);
            verify(jobRepository, never()).findByTitleIgnoreCaseAndCompanyNameIgnoreCase(any(), any());
        }
    }

    @Nested
    @DisplayName("Edge Cases & Safety Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("8. Missing/null URL does not cause an incorrect URL duplicate")
        void missingUrl_doesNotTriggerUrlDuplicate() {
            NormalizedJobCandidate candidate = createCandidate(JobSource.NAUKRI, "NK-55", "QA Engineer", "TestCo", "Dallas", "Manual testing", null);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, "NK-55")).thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("QA Engineer", "TestCo")).thenReturn(List.of());

            StoredJobDuplicateMatch match = detector.findStoredDuplicate(candidate);

            assertThat(match.isDuplicate()).isFalse();
            verify(jobRepository, never()).findFirstByJobUrl(any());
        }

        @Test
        @DisplayName("9. Blank external ID in unnormalized input does not fabricate identity")
        void blankExternalId_doesNotFabricateIdentity() {
            Job existing = createStoredJob(JobSource.OTHER, "VAL-100", "Architect", "Firm", "Seattle", "Desc", "https://example.com/job-1");
            com.joblivo.job.ingestion.JobIngestionCandidate unnormalized = com.joblivo.job.ingestion.JobIngestionCandidate.builder()
                    .source(JobSource.OTHER)
                    .externalJobId("   ")
                    .title("Different Title")
                    .companyName("Different Company")
                    .jobUrl("https://example.com/job-2")
                    .build();

            JobDuplicateResult result = detector.compare(existing, unnormalized);

            assertThat(result.classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
        }

        @Test
        @DisplayName("10. Reuses canonical URL normalization")
        void reusesCanonicalUrlNormalization() {
            Job stored = createStoredJob(JobSource.LINKEDIN, "LI-1", "Engineer", "Co", "Loc", "Desc", "https://example.com/job");
            NormalizedJobCandidate candidate = createCandidate(JobSource.NAUKRI, "NK-2", "Engineer", "Co", "Loc", "Desc", "  https://example.com/job  ");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, "NK-2")).thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/job")).thenReturn(Optional.of(stored));

            StoredJobDuplicateMatch match = detector.findStoredDuplicate(candidate);

            assertThat(match.classification()).isEqualTo(JobDuplicateClassification.EXACT_URL_DUPLICATE);
        }

        @Test
        @DisplayName("11. Reuses fingerprint generation with case and whitespace collapsing")
        void reusesFingerprintGeneration() {
            Job stored = createStoredJob(JobSource.LINKEDIN, "LI-1", "Security Engineer", "Shield Corp", "Chicago", "AppSec review", "https://example.com/li");
            NormalizedJobCandidate candidate = createCandidate(JobSource.NAUKRI, "NK-2", "security   engineer", "shield corp", "CHICAGO", "AppSec review", "https://example.com/nk");

            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, "NK-2")).thenReturn(Optional.empty());
            when(jobRepository.findFirstByJobUrl("https://example.com/nk")).thenReturn(Optional.empty());
            when(jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase("security engineer", "shield corp")).thenReturn(List.of(stored));

            StoredJobDuplicateMatch match = detector.findStoredDuplicate(candidate);

            assertThat(match.classification()).isEqualTo(JobDuplicateClassification.EXACT_CONTENT_DUPLICATE);
        }

        @Test
        @DisplayName("12. Duplicate lookup failure throws honestly without treating as NOT_DUPLICATE")
        void lookupFailure_propagatesHonestly() {
            NormalizedJobCandidate candidate = createCandidate(JobSource.LINKEDIN, "LI-1", "Title", "Company", "Loc", "Desc", "https://example.com/1");

            when(jobRepository.findBySourceAndExternalJobId(any(), any()))
                    .thenThrow(new QueryTimeoutException("Database query timeout"));

            assertThatThrownBy(() -> detector.findStoredDuplicate(candidate))
                    .isInstanceOf(QueryTimeoutException.class)
                    .hasMessageContaining("Database query timeout");
        }

        @Test
        @DisplayName("13. In-memory stored-job comparison evaluates priority correctly")
        void inMemoryComparison_evaluatesPriorityCorrectly() {
            Job job1 = createStoredJob(JobSource.LINKEDIN, "LI-1", "Dev", "Co", "Loc", "Desc", "https://example.com/1");
            Job job2 = createStoredJob(JobSource.NAUKRI, "NK-2", "Dev", "Co", "Loc", "Desc", "https://example.com/2");
            List<Job> stored = List.of(job1, job2);

            // Match job1 by source
            NormalizedJobCandidate cand1 = createCandidate(JobSource.LINKEDIN, "LI-1", "Other", "Co", "Loc", "Desc", "https://example.com/other");
            StoredJobDuplicateMatch match1 = detector.findStoredDuplicate(cand1, stored);
            assertThat(match1.classification()).isEqualTo(JobDuplicateClassification.EXACT_SOURCE_DUPLICATE);
            assertThat(match1.matchedJob()).isSameAs(job1);

            // Match job2 by URL
            NormalizedJobCandidate cand2 = createCandidate(JobSource.CUTSHORT, "CS-3", "Different", "OtherCo", "Loc", "Desc", "https://example.com/2");
            StoredJobDuplicateMatch match2 = detector.findStoredDuplicate(cand2, stored);
            assertThat(match2.classification()).isEqualTo(JobDuplicateClassification.EXACT_URL_DUPLICATE);
            assertThat(match2.matchedJob()).isSameAs(job2);

            // Match by content
            NormalizedJobCandidate cand3 = createCandidate(JobSource.ATS, "ATS-4", "Dev", "Co", "Loc", "Desc", "https://example.com/unique");
            StoredJobDuplicateMatch match3 = detector.findStoredDuplicate(cand3, stored);
            assertThat(match3.classification()).isEqualTo(JobDuplicateClassification.EXACT_CONTENT_DUPLICATE);

            // No match
            NormalizedJobCandidate cand4 = createCandidate(JobSource.ATS, "ATS-5", "Designer", "DesignCo", "Loc", "Desc", "https://example.com/design");
            StoredJobDuplicateMatch match4 = detector.findStoredDuplicate(cand4, stored);
            assertThat(match4.classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
        }

        @Test
        @DisplayName("14. Null candidate safely returns NOT_DUPLICATE")
        void nullCandidate_returnsNotDuplicate() {
            StoredJobDuplicateMatch match = detector.findStoredDuplicate(null);
            assertThat(match.isDuplicate()).isFalse();
            assertThat(match.classification()).isEqualTo(JobDuplicateClassification.NOT_DUPLICATE);
        }

        @Test
        @DisplayName("15. Unconfigured JobRepository throws IllegalStateException")
        void unconfiguredRepository_throwsIllegalStateException() {
            JobDuplicateDetector noRepoDetector = new JobDuplicateDetector();
            NormalizedJobCandidate candidate = createCandidate(JobSource.LINKEDIN, "LI-1", "Title", "Company", "Loc", "Desc", "https://example.com/1");

            assertThatThrownBy(() -> noRepoDetector.findStoredDuplicate(candidate))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JobRepository must be configured");
        }
    }
}
