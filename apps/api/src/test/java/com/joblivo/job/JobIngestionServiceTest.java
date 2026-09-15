package com.joblivo.job;

import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.ingestion.JobCandidate;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobIngestionService Unit Tests")
class JobIngestionServiceTest {

    @Mock
    private JobRepository jobRepository;

    private JobIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new JobIngestionService(jobRepository);
    }

    private JobCandidate.Builder sampleCandidateBuilder() {
        return JobCandidate.builder()
                .source(JobSource.LINKEDIN)
                .externalJobId("ext-linkedin-5001")
                .title("Staff Cloud Engineer")
                .companyName("FinTech Innovations")
                .recruiterName("Alice Recruiter")
                .description("Designing resilient multi-cloud infrastructure and microservices.")
                .location("New York, NY")
                .workMode(JobWorkMode.HYBRID)
                .employmentType(JobEmploymentType.FULL_TIME)
                .experienceMinYears(5)
                .experienceMaxYears(10)
                .salaryMin(BigDecimal.valueOf(160000))
                .salaryMax(BigDecimal.valueOf(210000))
                .salaryCurrency("USD")
                .salaryPeriod(SalaryPeriod.YEAR)
                .jobUrl("https://linkedin.com/jobs/view/5001")
                .companyUrl("https://fintechinnovations.com")
                .applicationMethod(JobApplicationMethod.ATS)
                .postedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .discoveredAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .lastSeenAt(Instant.now());
    }

    @Nested
    @DisplayName("Creation & Ingestion of New Jobs")
    class NewJobIngestionTests {

        @Test
        @DisplayName("Ingesting a candidate absent from the repository creates and persists a new Job")
        void ingestNewJob() {
            JobCandidate candidate = sampleCandidateBuilder().build();
            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-linkedin-5001"))
                    .thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
                Job jobToSave = invocation.getArgument(0);
                jobToSave.onCreate();
                return jobToSave;
            });

            JobResponse response = ingestionService.ingest(candidate);

            assertThat(response).isNotNull();
            assertThat(response.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(response.externalJobId()).isEqualTo("ext-linkedin-5001");
            assertThat(response.title()).isEqualTo("Staff Cloud Engineer");
            assertThat(response.companyName()).isEqualTo("FinTech Innovations");
            assertThat(response.recruiterName()).isEqualTo("Alice Recruiter");
            assertThat(response.location()).isEqualTo("New York, NY");
            assertThat(response.workMode()).isEqualTo(JobWorkMode.HYBRID);
            assertThat(response.employmentType()).isEqualTo(JobEmploymentType.FULL_TIME);
            assertThat(response.experienceMinYears()).isEqualTo(5);
            assertThat(response.experienceMaxYears()).isEqualTo(10);
            assertThat(response.salaryMin()).isEqualByComparingTo(BigDecimal.valueOf(160000));
            assertThat(response.salaryMax()).isEqualByComparingTo(BigDecimal.valueOf(210000));
            assertThat(response.salaryCurrency()).isEqualTo("USD");
            assertThat(response.salaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
            assertThat(response.jobUrl()).isEqualTo("https://linkedin.com/jobs/view/5001");
            assertThat(response.companyUrl()).isEqualTo("https://fintechinnovations.com");
            assertThat(response.applicationMethod()).isEqualTo(JobApplicationMethod.ATS);

            ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(jobCaptor.capture());
            Job captured = jobCaptor.getValue();
            assertThat(captured.getSource()).isEqualTo(JobSource.LINKEDIN);
            assertThat(captured.getExternalJobId()).isEqualTo("ext-linkedin-5001");
            assertThat(captured.getDiscoveredAt()).isEqualTo(candidate.discoveredAt());
            assertThat(captured.getLastSeenAt()).isEqualTo(candidate.lastSeenAt());
        }

        @Test
        @DisplayName("Ingesting candidate with null optional fields uses default enum states")
        void ingestNewJobWithMinimalFields() {
            JobCandidate candidate = JobCandidate.builder()
                    .source(JobSource.NAUKRI)
                    .externalJobId("naukri-777")
                    .title("Software Engineer")
                    .companyName("Tech Corp")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, "naukri-777"))
                    .thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobResponse response = ingestionService.ingest(candidate);

            assertThat(response.source()).isEqualTo(JobSource.NAUKRI);
            assertThat(response.externalJobId()).isEqualTo("naukri-777");
            assertThat(response.workMode()).isEqualTo(JobWorkMode.UNKNOWN);
            assertThat(response.employmentType()).isEqualTo(JobEmploymentType.UNKNOWN);
            assertThat(response.applicationMethod()).isEqualTo(JobApplicationMethod.UNKNOWN);
            assertThat(response.salaryMin()).isNull();
            assertThat(response.salaryMax()).isNull();
            assertThat(response.jobUrl()).isNull();
        }

        @Test
        @DisplayName("Ingesting a JobIngestionCandidate creates and persists a new Job")
        void ingestJobIngestionCandidate() {
            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.COMPANY_CAREERS)
                    .externalJobId("careers-101")
                    .title("Staff Site Reliability Engineer")
                    .companyName("Cloud Inc")
                    .rawWorkMode("remote")
                    .rawEmploymentType("full-time")
                    .salaryMin(BigDecimal.valueOf(180000))
                    .salaryMax(BigDecimal.valueOf(220000))
                    .salaryCurrency("USD")
                    .rawSalaryPeriod("yearly")
                    .jobUrl("https://cloudinc.com/jobs/101")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(JobSource.COMPANY_CAREERS, "careers-101"))
                    .thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobResponse response = ingestionService.ingest(candidate);

            assertThat(response.source()).isEqualTo(JobSource.COMPANY_CAREERS);
            assertThat(response.externalJobId()).isEqualTo("careers-101");
            assertThat(response.workMode()).isEqualTo(JobWorkMode.REMOTE);
            assertThat(response.employmentType()).isEqualTo(JobEmploymentType.FULL_TIME);
            assertThat(response.salaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
        }
    }

    @Nested
    @DisplayName("Idempotent Updates of Existing Jobs")
    class ExistingJobUpdateTests {

        @Test
        @DisplayName("Re-ingesting an existing job updates mutable attributes while preserving identity and discoveredAt")
        void updatesExistingJobAttributes() {
            Instant originalDiscovered = Instant.now().minus(5, ChronoUnit.DAYS);
            Instant originalLastSeen = Instant.now().minus(2, ChronoUnit.DAYS);

            Job existingJob = new Job(JobSource.LINKEDIN, "ext-linkedin-5001", "Old Title", "Old Company");
            existingJob.setDiscoveredAt(originalDiscovered);
            existingJob.setLastSeenAt(originalLastSeen);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-linkedin-5001"))
                    .thenReturn(Optional.of(existingJob));
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Instant newLastSeen = Instant.now();
            JobCandidate updateCandidate = sampleCandidateBuilder()
                    .title("Principal Cloud Engineer")
                    .companyName("FinTech Innovations Global")
                    .lastSeenAt(newLastSeen)
                    .build();

            JobResponse response = ingestionService.ingest(updateCandidate);

            assertThat(response.title()).isEqualTo("Principal Cloud Engineer");
            assertThat(response.companyName()).isEqualTo("FinTech Innovations Global");
            assertThat(existingJob.getDiscoveredAt()).isEqualTo(originalDiscovered); // Discovered timestamp preserved!
            assertThat(existingJob.getLastSeenAt()).isEqualTo(newLastSeen); // Last seen refreshed!
            assertThat(existingJob.getSource()).isEqualTo(JobSource.LINKEDIN);
            assertThat(existingJob.getExternalJobId()).isEqualTo("ext-linkedin-5001");
        }

        @Test
        @DisplayName("Re-ingesting existing job does not regress lastSeenAt if candidate has older timestamp")
        void doesNotRegressLastSeenAt() {
            Instant now = Instant.now();
            Instant discovered = now.minus(10, ChronoUnit.DAYS);
            Instant existingLastSeen = now;

            Job existingJob = new Job(JobSource.LINKEDIN, "ext-linkedin-5001", "Title", "Company");
            existingJob.setDiscoveredAt(discovered);
            existingJob.setLastSeenAt(existingLastSeen);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-linkedin-5001"))
                    .thenReturn(Optional.of(existingJob));
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobCandidate olderCandidate = sampleCandidateBuilder()
                    .discoveredAt(discovered)
                    .lastSeenAt(now.minus(2, ChronoUnit.DAYS))
                    .build();

            ingestionService.ingest(olderCandidate);

            assertThat(existingJob.getLastSeenAt()).isEqualTo(existingLastSeen);
        }

        @Test
        @DisplayName("Same external ID across different sources are treated as separate jobs")
        void differentSourcesSameExternalIdTreatedSeparately() {
            String sharedExternalId = "shared-id-9999";

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, sharedExternalId))
                    .thenReturn(Optional.empty());
            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, sharedExternalId))
                    .thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobCandidate linkedinCandidate = JobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId(sharedExternalId)
                    .title("Job from LinkedIn")
                    .companyName("Enterprise Ltd")
                    .build();

            JobCandidate naukriCandidate = JobCandidate.builder()
                    .source(JobSource.NAUKRI)
                    .externalJobId(sharedExternalId)
                    .title("Job from Naukri")
                    .companyName("Enterprise Ltd")
                    .build();

            JobResponse response1 = ingestionService.ingest(linkedinCandidate);
            JobResponse response2 = ingestionService.ingest(naukriCandidate);

            assertThat(response1.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(response2.source()).isEqualTo(JobSource.NAUKRI);
            verify(jobRepository).findBySourceAndExternalJobId(JobSource.LINKEDIN, sharedExternalId);
            verify(jobRepository).findBySourceAndExternalJobId(JobSource.NAUKRI, sharedExternalId);
            verify(jobRepository, times(2)).save(any(Job.class));
        }

        @Test
        @DisplayName("Data Loss Protection: missing candidate fields do NOT overwrite existing populated fields")
        void dataLossProtectionPreservesExistingPopulatedFields() {
            Instant discovered = Instant.now().minus(5, ChronoUnit.DAYS);
            Job existingJob = new Job(JobSource.LINKEDIN, "ext-linkedin-5001", "Existing Title", "Existing Co");
            existingJob.setDiscoveredAt(discovered);
            existingJob.setDescription("Crucial existing job description");
            existingJob.setRecruiterName("Jane Recruiter");
            existingJob.setLocation("San Francisco, CA");
            existingJob.setWorkMode(JobWorkMode.HYBRID);
            existingJob.setEmploymentType(JobEmploymentType.FULL_TIME);
            existingJob.setCompanyUrl("https://existingco.com");
            existingJob.setSalaryMin(BigDecimal.valueOf(140000));
            existingJob.setSalaryMax(BigDecimal.valueOf(170000));
            existingJob.setSalaryCurrency("USD");
            existingJob.setSalaryPeriod(SalaryPeriod.YEAR);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-linkedin-5001"))
                    .thenReturn(Optional.of(existingJob));
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Candidate payload with only new title, everything else null/blank/unknown
            JobIngestionCandidate sparseUpdate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-linkedin-5001")
                    .title("Updated Title")
                    .companyName("Existing Co")
                    .build();

            JobResponse response = ingestionService.ingest(sparseUpdate);

            // Updated title
            assertThat(response.title()).isEqualTo("Updated Title");
            // Preserved fields from existing job!
            assertThat(existingJob.getDescription()).isEqualTo("Crucial existing job description");
            assertThat(existingJob.getRecruiterName()).isEqualTo("Jane Recruiter");
            assertThat(existingJob.getLocation()).isEqualTo("San Francisco, CA");
            assertThat(existingJob.getWorkMode()).isEqualTo(JobWorkMode.HYBRID);
            assertThat(existingJob.getEmploymentType()).isEqualTo(JobEmploymentType.FULL_TIME);
            assertThat(existingJob.getCompanyUrl()).isEqualTo("https://existingco.com");
            assertThat(existingJob.getSalaryMin()).isEqualByComparingTo(BigDecimal.valueOf(140000));
            assertThat(existingJob.getSalaryMax()).isEqualByComparingTo(BigDecimal.valueOf(170000));
        }

        @Test
        @DisplayName("Data Loss Protection: UNKNOWN enum states do NOT overwrite existing known workMode and employmentType")
        void dataLossProtectionPreservesKnownEnumStates() {
            Job existingJob = new Job(JobSource.LINKEDIN, "ext-linkedin-5001", "Title", "Company");
            existingJob.setWorkMode(JobWorkMode.REMOTE);
            existingJob.setEmploymentType(JobEmploymentType.CONTRACT);
            existingJob.setApplicationMethod(JobApplicationMethod.ATS);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-linkedin-5001"))
                    .thenReturn(Optional.of(existingJob));
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobCandidate candidate = JobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-linkedin-5001")
                    .title("Title")
                    .companyName("Company")
                    .workMode(JobWorkMode.UNKNOWN)
                    .employmentType(JobEmploymentType.UNKNOWN)
                    .applicationMethod(JobApplicationMethod.UNKNOWN)
                    .build();

            ingestionService.ingest(candidate);

            assertThat(existingJob.getWorkMode()).isEqualTo(JobWorkMode.REMOTE);
            assertThat(existingJob.getEmploymentType()).isEqualTo(JobEmploymentType.CONTRACT);
            assertThat(existingJob.getApplicationMethod()).isEqualTo(JobApplicationMethod.ATS);
        }

        @Test
        @DisplayName("Updating salaryMin checks against existing salaryMax and rejects invalid range")
        void updateSalaryMinChecksAgainstExistingSalaryMax() {
            Job existingJob = new Job(JobSource.LINKEDIN, "ext-linkedin-5001", "Title", "Company");
            existingJob.setSalaryMin(BigDecimal.valueOf(100000));
            existingJob.setSalaryMax(BigDecimal.valueOf(150000));

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-linkedin-5001"))
                    .thenReturn(Optional.of(existingJob));

            // Candidate updates salaryMin to 180000 (which exceeds existing salaryMax 150000)
            JobCandidate candidate = JobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-linkedin-5001")
                    .title("Title")
                    .companyName("Company")
                    .salaryMin(BigDecimal.valueOf(180000))
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("salaryMin (180000) must not exceed salaryMax (150000)");
        }
    }

    @Nested
    @DisplayName("Validation Rules")
    class ValidationTests {

        @Test
        @DisplayName("Null candidate throws NullPointerException")
        void nullCandidateThrowsNpe() {
            assertThatThrownBy(() -> ingestionService.ingest((JobCandidate) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("JobCandidate must not be null");

            assertThatThrownBy(() -> ingestionService.ingest((JobIngestionCandidate) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("JobIngestionCandidate must not be null");
        }

        @Test
        @DisplayName("Invalid URL scheme throws JobValidationException")
        void invalidUrlSchemeThrowsException() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .jobUrl("ftp://example.com/job/1")
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("jobUrl must use HTTP or HTTPS scheme");
        }

        @Test
        @DisplayName("Malformed URL throws JobValidationException")
        void malformedUrlThrowsException() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .jobUrl("https://")
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("malformed URL");
        }

        @Test
        @DisplayName("URL without host throws JobValidationException")
        void urlWithoutHostThrowsException() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .jobUrl("https:///jobs/view/5001")
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("must specify a valid host");
        }

        @Test
        @DisplayName("Negative salary min throws JobValidationException")
        void negativeSalaryMinThrowsException() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .salaryMin(BigDecimal.valueOf(-1000))
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("salaryMin must not be negative");
        }

        @Test
        @DisplayName("Negative salary max throws JobValidationException")
        void negativeSalaryMaxThrowsException() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .salaryMax(BigDecimal.valueOf(-500))
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("salaryMax must not be negative");
        }

        @Test
        @DisplayName("Salary min exceeding salary max throws JobValidationException")
        void invertedSalaryRangeThrowsException() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .salaryMin(BigDecimal.valueOf(200000))
                    .salaryMax(BigDecimal.valueOf(150000))
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("salaryMin (200000) must not exceed salaryMax (150000)");
        }

        @Test
        @DisplayName("Negative experience min throws JobValidationException")
        void negativeExperienceMinThrowsException() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .experienceMinYears(-1)
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("experienceMinYears must not be negative");
        }

        @Test
        @DisplayName("Negative experience max throws JobValidationException")
        void negativeExperienceMaxThrowsException() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .experienceMaxYears(-2)
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("experienceMaxYears must not be negative");
        }

        @Test
        @DisplayName("Experience min exceeding experience max throws JobValidationException")
        void invertedExperienceRangeThrowsException() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .experienceMinYears(8)
                    .experienceMaxYears(4)
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("experienceMinYears (8) must not exceed experienceMaxYears (4)");
        }

        @Test
        @DisplayName("expiresAt preceding postedAt throws JobValidationException")
        void expiresAtPrecedingPostedAtThrowsException() {
            Instant now = Instant.now();
            JobCandidate candidate = sampleCandidateBuilder()
                    .postedAt(now)
                    .expiresAt(now.minus(1, ChronoUnit.HOURS))
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("cannot precede postedAt");
        }

        @Test
        @DisplayName("lastSeenAt preceding discoveredAt throws JobValidationException")
        void lastSeenAtPrecedingDiscoveredAtThrowsException() {
            Instant now = Instant.now();
            JobCandidate candidate = sampleCandidateBuilder()
                    .discoveredAt(now)
                    .lastSeenAt(now.minus(1, ChronoUnit.MINUTES))
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("cannot precede discoveredAt");
        }

        @Test
        @DisplayName("Blank externalJobId throws JobValidationException and prevents job persistence")
        void blankExternalJobIdThrowsException() {
            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("   ")
                    .title("Cloud Eng")
                    .companyName("FinTech")
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId must not be null or blank");

            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("Null externalJobId throws JobValidationException and prevents job persistence")
        void nullExternalJobIdThrowsException() {
            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId(null)
                    .title("Cloud Eng")
                    .companyName("FinTech")
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId must not be null or blank");

            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("Whitespace in externalJobId is trimmed deterministically before persistence")
        void whitespaceInExternalJobIdIsTrimmed() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .externalJobId("   ext-linkedin-5001   ")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-linkedin-5001"))
                    .thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobResponse response = ingestionService.ingest(candidate);

            assertThat(response.externalJobId()).isEqualTo("ext-linkedin-5001");
            verify(jobRepository).findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-linkedin-5001");
        }
    }

    @Nested
    @DisplayName("Batch Ingestion")
    class BatchIngestionTests {

        @Test
        @DisplayName("ingestAll with null or empty list returns empty list")
        void ingestAllEmptyOrNullReturnsEmpty() {
            assertThat(ingestionService.ingestAll(null)).isEmpty();
            assertThat(ingestionService.ingestAll(List.of())).isEmpty();
            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("ingestAll processes each candidate and returns list of responses")
        void ingestAllProcessesBatch() {
            JobCandidate candidate1 = JobCandidate.builder()
                    .source(JobSource.CUTSHORT)
                    .externalJobId("cs-1")
                    .title("Backend Lead")
                    .companyName("Alpha Labs")
                    .build();

            JobCandidate candidate2 = JobCandidate.builder()
                    .source(JobSource.INSTAHYRE)
                    .externalJobId("insta-2")
                    .title("Data Engineer")
                    .companyName("Beta Analytics")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(any(), any())).thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            List<JobResponse> responses = ingestionService.ingestAll(List.of(candidate1, candidate2));

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).title()).isEqualTo("Backend Lead");
            assertThat(responses.get(1).title()).isEqualTo("Data Engineer");
            verify(jobRepository, times(2)).save(any(Job.class));
        }

        @Test
        @DisplayName("ingestAllCandidates processes batch of JobIngestionCandidate")
        void ingestAllCandidatesProcessesBatch() {
            JobIngestionCandidate c1 = JobIngestionCandidate.builder()
                    .source(JobSource.ATS)
                    .externalJobId("ats-1")
                    .title("Security Engineer")
                    .companyName("Cyber Guard")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(any(), any())).thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            List<JobResponse> responses = ingestionService.ingestAllCandidates(List.of(c1));

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).title()).isEqualTo("Security Engineer");
        }
    }

    @Nested
    @DisplayName("Canonical Normalization Boundary Ingestion Tests")
    class CanonicalNormalizationBoundaryTests {

        @Test
        @DisplayName("22. Normalized values are applied at the canonical normalization boundary during ingestion")
        void normalizedValuesAppliedAtIngestionBoundary() {
            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-norm-22")
                    .companyName("   Acme    Technologies   ")
                    .title("   Senior   AWS  Cloud   Engineer   ")
                    .location("   Bengaluru,    Karnataka   ")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-norm-22"))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
            when(jobRepository.save(jobCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            JobResponse response = ingestionService.ingest(candidate);

            assertThat(response.companyName()).isEqualTo("Acme Technologies");
            assertThat(response.title()).isEqualTo("Senior AWS Cloud Engineer");
            assertThat(response.location()).isEqualTo("Bengaluru, Karnataka");

            Job captured = jobCaptor.getValue();
            assertThat(captured.getCompanyName()).isEqualTo("Acme Technologies");
            assertThat(captured.getTitle()).isEqualTo("Senior AWS Cloud Engineer");
            assertThat(captured.getLocation()).isEqualTo("Bengaluru, Karnataka");
        }

        @Test
        @DisplayName("23. Source adapters do not duplicate normalization logic; un-normalized candidate is normalized safely")
        void rawCandidateFromAdapterNormalizedWithoutAdapterDuplication() {
            JobIngestionCandidate rawFromAdapter = JobIngestionCandidate.builder()
                    .source(JobSource.NAUKRI)
                    .externalJobId("naukri-999")
                    .companyName("Stripe,\u00A0Inc.")
                    .title("Staff\u00A0Software\u00A0Engineer (Platform)")
                    .location("Remote\u00A0-\u00A0India")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(JobSource.NAUKRI, "naukri-999"))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
            when(jobRepository.save(jobCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            JobResponse response = ingestionService.ingest(rawFromAdapter);

            assertThat(response.companyName()).isEqualTo("Stripe, Inc.");
            assertThat(response.title()).isEqualTo("Staff Software Engineer (Platform)");
            assertThat(response.location()).isEqualTo("Remote - India");
        }

        @Test
        @DisplayName("24. Persistence receives normalized candidate data according to existing architecture")
        void persistenceReceivesNormalizedCandidateData() {
            JobCandidate candidate = sampleCandidateBuilder()
                    .companyName("   Google   LLC   ")
                    .title("   Lead   DevOps   Architect   ")
                    .location("   Mountain   View,   CA   ")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(any(), any()))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
            when(jobRepository.save(jobCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            ingestionService.ingest(candidate);

            Job savedJob = jobCaptor.getValue();
            assertThat(savedJob.getCompanyName()).isEqualTo("Google LLC");
            assertThat(savedJob.getTitle()).isEqualTo("Lead DevOps Architect");
            assertThat(savedJob.getLocation()).isEqualTo("Mountain View, CA");
        }

        @Test
        @DisplayName("25. Repeated ingestion remains deterministic")
        void repeatedIngestionRemainsDeterministic() {
            JobIngestionCandidate candidate1 = JobIngestionCandidate.builder()
                    .source(JobSource.CUTSHORT)
                    .externalJobId("cs-repeat-1")
                    .companyName("  Databricks  Inc.  ")
                    .title("  Principal  Data  Scientist  ")
                    .location("  San  Francisco,  CA  ")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(JobSource.CUTSHORT, "cs-repeat-1"))
                    .thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobResponse firstResponse = ingestionService.ingest(candidate1);

            Job existingJob = new Job(JobSource.CUTSHORT, "cs-repeat-1", firstResponse.title(), firstResponse.companyName());
            existingJob.setLocation(firstResponse.location());

            when(jobRepository.findBySourceAndExternalJobId(JobSource.CUTSHORT, "cs-repeat-1"))
                    .thenReturn(Optional.of(existingJob));

            JobIngestionCandidate candidate2 = JobIngestionCandidate.builder()
                    .source(JobSource.CUTSHORT)
                    .externalJobId("cs-repeat-1")
                    .companyName("Databricks  Inc.")
                    .title("Principal  Data  Scientist")
                    .location("San  Francisco,  CA")
                    .build();

            JobResponse secondResponse = ingestionService.ingest(candidate2);

            assertThat(firstResponse.companyName()).isEqualTo(secondResponse.companyName());
            assertThat(firstResponse.title()).isEqualTo(secondResponse.title());
            assertThat(firstResponse.location()).isEqualTo(secondResponse.location());
            assertThat(secondResponse.companyName()).isEqualTo("Databricks Inc.");
            assertThat(secondResponse.title()).isEqualTo("Principal Data Scientist");
            assertThat(secondResponse.location()).isEqualTo("San Francisco, CA");
        }

        @Test
        @DisplayName("26. Same source data produces the exact same normalized result")
        void sameSourceDataProducesIdenticalNormalizedResult() {
            JobIngestionCandidate c = JobIngestionCandidate.builder()
                    .source(JobSource.COMPANY_CAREERS)
                    .externalJobId("cc-deterministic")
                    .companyName("  OpenAI  \t Global  LLC  ")
                    .title("  Senior   Research   Scientist   (AI/ML)  ")
                    .location("  San   Francisco,   CA   -   Hybrid  ")
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(JobSource.COMPANY_CAREERS, "cc-deterministic"))
                    .thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobResponse r1 = ingestionService.ingest(c);
            JobResponse r2 = ingestionService.ingest(c);

            assertThat(r1.companyName()).isEqualTo(r2.companyName()).isEqualTo("OpenAI Global LLC");
            assertThat(r1.title()).isEqualTo(r2.title()).isEqualTo("Senior Research Scientist (AI/ML)");
            assertThat(r1.location()).isEqualTo(r2.location()).isEqualTo("San Francisco, CA - Hybrid");
        }
    }

    @Nested
    @DisplayName("Prompt 53 Content Integrity & Update Safety Tests")
    class ContentIntegrityAndSafetyTests {

        @Test
        @DisplayName("31. Candidate with title exceeding database column limit (255) throws VALUE_TOO_LONG")
        void titleExceeding255ThrowsException() {
            String excessiveTitle = "Senior Software Engineer ".repeat(15); // > 255 chars
            JobCandidate candidate = JobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-too-long-title")
                    .title(excessiveTitle)
                    .companyName("Acme Corp")
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .satisfies(ex -> {
                        JobValidationException jve = (JobValidationException) ex;
                        assertThat(jve.getFailureCategory()).isEqualTo("VALUE_TOO_LONG");
                    });

            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("32. Invalid existing-job update does not partially corrupt the existing Job (dates contradiction)")
        void invalidDateUpdateDoesNotCorruptExistingJob() {
            Instant existingPostedAt = Instant.parse("2026-09-10T10:00:00Z");
            Job existingJob = new Job(JobSource.LINKEDIN, "ext-corrupt-test", "Backend Engineer", "Original Corp");
            existingJob.setPostedAt(existingPostedAt);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-corrupt-test"))
                    .thenReturn(Optional.of(existingJob));

            Instant invalidExpiresAt = Instant.parse("2026-09-01T10:00:00Z"); // Precedes existing postedAt
            JobCandidate invalidUpdate = JobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-corrupt-test")
                    .title("Updated Title")
                    .companyName("Updated Corp")
                    .expiresAt(invalidExpiresAt)
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(invalidUpdate))
                    .isInstanceOf(JobValidationException.class)
                    .satisfies(ex -> {
                        JobValidationException jve = (JobValidationException) ex;
                        assertThat(jve.getFailureCategory()).isEqualTo("INVALID_DATE_RANGE");
                    });

            // Verify existing job was not saved/corrupted
            verify(jobRepository, never()).save(any());
            assertThat(existingJob.getExpiresAt()).isNull();
            assertThat(existingJob.getPostedAt()).isEqualTo(existingPostedAt);
        }

        @Test
        @DisplayName("32b. Invalid experience update does not partially corrupt existing Job")
        void invalidExperienceUpdateDoesNotCorruptExistingJob() {
            Job existingJob = new Job(JobSource.LINKEDIN, "ext-exp-corrupt", "Engineer", "Acme");
            existingJob.setExperienceMaxYears(3);

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-exp-corrupt"))
                    .thenReturn(Optional.of(existingJob));

            JobCandidate invalidUpdate = JobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-exp-corrupt")
                    .title("Engineer")
                    .companyName("Acme")
                    .experienceMinYears(5) // exceeds existing max of 3
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(invalidUpdate))
                    .isInstanceOf(JobValidationException.class)
                    .satisfies(ex -> {
                        JobValidationException jve = (JobValidationException) ex;
                        assertThat(jve.getFailureCategory()).isEqualTo("INVALID_EXPERIENCE_RANGE");
                    });

            verify(jobRepository, never()).save(any());
            assertThat(existingJob.getExperienceMinYears()).isNull();
            assertThat(existingJob.getExperienceMaxYears()).isEqualTo(3);
        }

        @Test
        @DisplayName("32c. Invalid salary update does not partially corrupt existing Job")
        void invalidSalaryUpdateDoesNotCorruptExistingJob() {
            Job existingJob = new Job(JobSource.LINKEDIN, "ext-sal-corrupt", "Engineer", "Acme");
            existingJob.setSalaryMax(BigDecimal.valueOf(50000));

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-sal-corrupt"))
                    .thenReturn(Optional.of(existingJob));

            JobCandidate invalidUpdate = JobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-sal-corrupt")
                    .title("Engineer")
                    .companyName("Acme")
                    .salaryMin(BigDecimal.valueOf(80000)) // exceeds existing max of 50000
                    .build();

            assertThatThrownBy(() -> ingestionService.ingest(invalidUpdate))
                    .isInstanceOf(JobValidationException.class)
                    .satisfies(ex -> {
                        JobValidationException jve = (JobValidationException) ex;
                        assertThat(jve.getFailureCategory()).isEqualTo("INVALID_SALARY_RANGE");
                    });

            verify(jobRepository, never()).save(any());
            assertThat(existingJob.getSalaryMin()).isNull();
            assertThat(existingJob.getSalaryMax()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        }

        @Test
        @DisplayName("33. Valid candidate continues to persist")
        void validCandidateContinuesToPersist() {
            JobCandidate candidate = JobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-valid-persist")
                    .title("Full Stack Developer")
                    .companyName("Tech Innovators Inc.")
                    .jobUrl("https://techinnovators.com/careers/101")
                    .salaryMin(BigDecimal.valueOf(90000))
                    .salaryMax(BigDecimal.valueOf(130000))
                    .salaryCurrency("USD")
                    .salaryPeriod(SalaryPeriod.YEAR)
                    .build();

            when(jobRepository.findBySourceAndExternalJobId(JobSource.LINKEDIN, "ext-valid-persist"))
                    .thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

            JobResponse response = ingestionService.ingest(candidate);

            assertThat(response).isNotNull();
            assertThat(response.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(response.externalJobId()).isEqualTo("ext-valid-persist");
            assertThat(response.title()).isEqualTo("Full Stack Developer");
            assertThat(response.companyName()).isEqualTo("Tech Innovators Inc.");
            verify(jobRepository, times(1)).save(any(Job.class));
        }
    }
}
