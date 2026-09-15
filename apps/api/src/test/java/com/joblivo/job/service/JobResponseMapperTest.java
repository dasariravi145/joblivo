package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobFreshnessStatus;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobResponseMapper Unit Tests")
class JobResponseMapperTest {

    private JobFreshnessEvaluator freshnessEvaluator;
    private JobResponseMapper mapper;

    @BeforeEach
    void setUp() {
        freshnessEvaluator = new JobFreshnessEvaluator();
        mapper = new JobResponseMapper(freshnessEvaluator);
    }

    private Job createFullJob(UUID id) {
        try {
            Job job = new Job(JobSource.LINKEDIN, "ext-full-123", "Lead Cloud Architect", "Acme Corporation");
            job.setRecruiterName("Jane Recruiter");
            job.setDescription("Design and build scalable distributed cloud services.");
            job.setLocation("Bengaluru, KA, India");
            job.setWorkMode(JobWorkMode.HYBRID);
            job.setEmploymentType(JobEmploymentType.FULL_TIME);
            job.setExperienceMinYears(5);
            job.setExperienceMaxYears(10);
            job.setSalaryMin(BigDecimal.valueOf(2500000));
            job.setSalaryMax(BigDecimal.valueOf(3500000));
            job.setSalaryCurrency("INR");
            job.setSalaryPeriod(SalaryPeriod.YEAR);
            job.setJobUrl("https://linkedin.com/jobs/view/ext-full-123");
            job.setCompanyUrl("https://acme.example.com");
            job.setPostedAt(Instant.now().minus(2, ChronoUnit.DAYS));
            job.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
            job.setDiscoveredAt(Instant.now().minus(2, ChronoUnit.DAYS));
            job.setLastSeenAt(Instant.now().minus(1, ChronoUnit.HOURS));
            job.setApplicationMethod(JobApplicationMethod.EXTERNAL_COMPANY_SITE);

            setField(job, "id", id);
            setField(job, "createdAt", Instant.now().minus(2, ChronoUnit.DAYS));
            setField(job, "updatedAt", Instant.now().minus(1, ChronoUnit.HOURS));

            return job;
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct test Job", e);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = Job.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nested
    @DisplayName("Canonical Job Read Model Mapping")
    class CanonicalMappingTests {

        @Test
        @DisplayName("Maps every existing Job field correctly to JobResponse")
        void toResponse_MapsAllFieldsCorrectly() {
            UUID id = UUID.randomUUID();
            Job job = createFullJob(id);

            JobResponse response = mapper.toResponse(job);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(id);
            assertThat(response.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(response.externalJobId()).isEqualTo("ext-full-123");
            assertThat(response.title()).isEqualTo("Lead Cloud Architect");
            assertThat(response.companyName()).isEqualTo("Acme Corporation");
            assertThat(response.recruiterName()).isEqualTo("Jane Recruiter");
            assertThat(response.description()).isEqualTo("Design and build scalable distributed cloud services.");
            assertThat(response.location()).isEqualTo("Bengaluru, KA, India");
            assertThat(response.workMode()).isEqualTo(JobWorkMode.HYBRID);
            assertThat(response.employmentType()).isEqualTo(JobEmploymentType.FULL_TIME);
            assertThat(response.experienceMinYears()).isEqualTo(5);
            assertThat(response.experienceMaxYears()).isEqualTo(10);
            assertThat(response.salaryMin()).isEqualByComparingTo(BigDecimal.valueOf(2500000));
            assertThat(response.salaryMax()).isEqualByComparingTo(BigDecimal.valueOf(3500000));
            assertThat(response.salaryCurrency()).isEqualTo("INR");
            assertThat(response.salaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
            assertThat(response.jobUrl()).isEqualTo("https://linkedin.com/jobs/view/ext-full-123");
            assertThat(response.companyUrl()).isEqualTo("https://acme.example.com");
            assertThat(response.postedAt()).isEqualTo(job.getPostedAt());
            assertThat(response.expiresAt()).isEqualTo(job.getExpiresAt());
            assertThat(response.discoveredAt()).isEqualTo(job.getDiscoveredAt());
            assertThat(response.lastSeenAt()).isEqualTo(job.getLastSeenAt());
            assertThat(response.applicationMethod()).isEqualTo(JobApplicationMethod.EXTERNAL_COMPANY_SITE);
            assertThat(response.createdAt()).isEqualTo(job.getCreatedAt());
            assertThat(response.updatedAt()).isEqualTo(job.getUpdatedAt());
            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.ACTIVE);
            assertThat(response.authenticityAssessment()).isNotNull();
            assertThat(response.authenticityAssessment().overallAssessment()).isEqualTo(com.joblivo.job.model.JobAuthenticityOutcome.NO_WARNING);
            assertThat(response.jobDescriptionIntelligence()).isNotNull();
            assertThat(response.jobDescriptionIntelligence().originalDescriptionPresent()).isTrue();
        }

        @Test
        @DisplayName("Null optional fields remain null without fabricated values")
        void toResponse_NullFieldsRemainNull() throws Exception {
            UUID id = UUID.randomUUID();
            Job job = new Job(JobSource.OTHER, "cust-sparse-99", "Software Engineer", "Startup Inc");
            setField(job, "id", id);
            // All optional fields are null
            job.setRecruiterName(null);
            job.setDescription(null);
            job.setLocation(null);
            job.setExperienceMinYears(null);
            job.setExperienceMaxYears(null);
            job.setSalaryMin(null);
            job.setSalaryMax(null);
            job.setSalaryCurrency(null);
            job.setSalaryPeriod(null);
            job.setJobUrl(null);
            job.setCompanyUrl(null);
            job.setPostedAt(null);
            job.setExpiresAt(null);

            JobResponse response = mapper.toResponse(job);

            assertThat(response).isNotNull();
            assertThat(response.recruiterName()).isNull();
            assertThat(response.description()).isNull();
            assertThat(response.location()).isNull();
            assertThat(response.experienceMinYears()).isNull();
            assertThat(response.experienceMaxYears()).isNull();
            assertThat(response.salaryMin()).isNull();
            assertThat(response.salaryMax()).isNull();
            assertThat(response.salaryCurrency()).isNull();
            assertThat(response.salaryPeriod()).isNull();
            assertThat(response.jobUrl()).isNull();
            assertThat(response.companyUrl()).isNull();
            assertThat(response.postedAt()).isNull();
            assertThat(response.expiresAt()).isNull();
        }

        @Test
        @DisplayName("Null job entity safely returns null")
        void toResponse_NullJob_ReturnsNull() {
            assertThat(mapper.toResponse(null)).isNull();
            assertThat(mapper.toResponse(null, JobFreshnessStatus.ACTIVE)).isNull();
        }

        @Test
        @DisplayName("Preserves source identity exactly without rewriting or synthetic IDs")
        void toResponse_PreservesSourceIdentity() throws Exception {
            UUID id = UUID.randomUUID();
            Job job = new Job(JobSource.COMPANY_CAREERS, "rec-007-complex/slug?abc=1", "Site Reliability Engineer", "Reliable Co");
            job.setJobUrl("https://company.careers.com/o/sre-007");
            job.setCompanyUrl("https://reliable.co.uk");
            setField(job, "id", id);

            JobResponse response = mapper.toResponse(job);

            assertThat(response.externalJobId()).isEqualTo("rec-007-complex/slug?abc=1");
            assertThat(response.source()).isEqualTo(JobSource.COMPANY_CAREERS);
            assertThat(response.jobUrl()).isEqualTo("https://company.careers.com/o/sre-007");
            assertThat(response.companyUrl()).isEqualTo("https://reliable.co.uk");
        }
    }

    @Nested
    @DisplayName("Freshness Status Derivation via Canonical Evaluator")
    class FreshnessStatusTests {

        @Test
        @DisplayName("Derives ACTIVE freshness status correctly")
        void derivesActiveStatus() throws Exception {
            Job job = new Job(JobSource.LINKEDIN, "ext-active", "Senior Developer", "Tech Ltd");
            Instant now = Instant.now();
            job.setPostedAt(now.minus(2, ChronoUnit.DAYS));
            job.setExpiresAt(now.plus(20, ChronoUnit.DAYS));
            job.setDiscoveredAt(now.minus(2, ChronoUnit.DAYS));
            job.setLastSeenAt(now.minus(1, ChronoUnit.HOURS));

            JobResponse response = mapper.toResponse(job);

            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.ACTIVE);
        }

        @Test
        @DisplayName("Derives STALE freshness status correctly")
        void derivesStaleStatus() throws Exception {
            Job job = new Job(JobSource.LINKEDIN, "ext-stale", "Senior Developer", "Tech Ltd");
            Instant now = Instant.now();
            job.setPostedAt(now.minus(60, ChronoUnit.DAYS));
            job.setExpiresAt(null);
            job.setDiscoveredAt(now.minus(60, ChronoUnit.DAYS));
            job.setLastSeenAt(now.minus(35, ChronoUnit.DAYS));

            JobResponse response = mapper.toResponse(job);

            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.STALE);
        }

        @Test
        @DisplayName("Derives EXPIRED freshness status correctly")
        void derivesExpiredStatus() throws Exception {
            Job job = new Job(JobSource.LINKEDIN, "ext-expired", "Senior Developer", "Tech Ltd");
            Instant now = Instant.now();
            job.setPostedAt(now.minus(15, ChronoUnit.DAYS));
            job.setExpiresAt(now.minus(1, ChronoUnit.DAYS));
            job.setDiscoveredAt(now.minus(15, ChronoUnit.DAYS));
            job.setLastSeenAt(now.minus(2, ChronoUnit.DAYS));

            JobResponse response = mapper.toResponse(job);

            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.EXPIRED);
        }

        @Test
        @DisplayName("Derives UNKNOWN freshness status correctly when timestamps are absent")
        void derivesUnknownStatus() throws Exception {
            Job job = new Job(JobSource.OTHER, "ext-unknown", "Senior Developer", "Tech Ltd");
            job.setPostedAt(null);
            job.setExpiresAt(null);
            setField(job, "discoveredAt", null);
            setField(job, "lastSeenAt", null);

            JobResponse response = mapper.toResponse(job);

            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @ParameterizedTest
        @EnumSource(JobFreshnessStatus.class)
        @DisplayName("Explicit freshness status is preserved verbatim when passed to toResponse(job, status)")
        void explicitFreshnessStatusPreserved(JobFreshnessStatus expectedStatus) throws Exception {
            UUID id = UUID.randomUUID();
            Job job = new Job(JobSource.LINKEDIN, "ext-explicit", "Engineer", "Firm");
            setField(job, "id", id);

            JobResponse response = mapper.toResponse(job, expectedStatus);

            assertThat(response.freshnessStatus()).isEqualTo(expectedStatus);
        }
    }

    @Nested
    @DisplayName("Read Immutability & Side-Effect Freedom")
    class ReadImmutabilityTests {

        @Test
        @DisplayName("Mapping does not mutate lastSeenAt, discoveredAt, postedAt, or expiresAt")
        void mappingDoesNotMutateEntityTimestamps() throws Exception {
            UUID id = UUID.randomUUID();
            Job job = createFullJob(id);

            Instant originalPostedAt = job.getPostedAt();
            Instant originalExpiresAt = job.getExpiresAt();
            Instant originalDiscoveredAt = job.getDiscoveredAt();
            Instant originalLastSeenAt = job.getLastSeenAt();

            JobResponse response = mapper.toResponse(job);

            assertThat(job.getPostedAt()).isEqualTo(originalPostedAt);
            assertThat(job.getExpiresAt()).isEqualTo(originalExpiresAt);
            assertThat(job.getDiscoveredAt()).isEqualTo(originalDiscoveredAt);
            assertThat(job.getLastSeenAt()).isEqualTo(originalLastSeenAt);

            assertThat(response.postedAt()).isEqualTo(originalPostedAt);
            assertThat(response.expiresAt()).isEqualTo(originalExpiresAt);
            assertThat(response.discoveredAt()).isEqualTo(originalDiscoveredAt);
            assertThat(response.lastSeenAt()).isEqualTo(originalLastSeenAt);
        }
    }

    @Nested
    @DisplayName("Authenticity Assessment Mapping")
    class AuthenticityMappingTests {

        @Test
        @DisplayName("toResponse computes and attaches authenticity assessment")
        void toResponse_AttachesComputedAuthenticityAssessment() throws Exception {
            UUID id = UUID.randomUUID();
            Job job = createFullJob(id);

            JobResponse response = mapper.toResponse(job);

            assertThat(response).isNotNull();
            assertThat(response.authenticityAssessment()).isNotNull();
            assertThat(response.authenticityAssessment().overallAssessment()).isEqualTo(com.joblivo.job.model.JobAuthenticityOutcome.NO_WARNING);
            assertThat(response.authenticityAssessment().highestRisk()).isEqualTo(com.joblivo.job.model.JobAuthenticityRiskLevel.NONE);
        }

        @Test
        @DisplayName("toResponse preserves explicit authenticity assessment verbatim")
        void toResponse_PreservesExplicitAuthenticityAssessment() throws Exception {
            UUID id = UUID.randomUUID();
            Job job = createFullJob(id);
            com.joblivo.job.model.JobAuthenticityAssessment explicitAssessment = com.joblivo.job.model.JobAuthenticityAssessment.fromSignals(
                    List.of(com.joblivo.job.model.JobAuthenticitySignal.of(
                            com.joblivo.job.model.JobAuthenticitySignalType.MISSING_JOB_URL,
                            "test reason"
                    )),
                    false
            );

            JobResponse response = mapper.toResponse(job, JobFreshnessStatus.ACTIVE, explicitAssessment);

            assertThat(response).isNotNull();
            assertThat(response.authenticityAssessment()).isSameAs(explicitAssessment);
            assertThat(response.authenticityAssessment().signals()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Description Intelligence Mapping")
    class DescriptionIntelligenceMappingTests {

        @Test
        @DisplayName("toResponse computes and attaches description intelligence")
        void toResponse_AttachesComputedDescriptionIntelligence() throws Exception {
            UUID id = UUID.randomUUID();
            Job job = createFullJob(id);
            job.setDescription("Design and build scalable distributed cloud services in Bengaluru.");

            JobResponse response = mapper.toResponse(job);

            assertThat(response).isNotNull();
            assertThat(response.jobDescriptionIntelligence()).isNotNull();
            assertThat(response.jobDescriptionIntelligence().originalDescriptionPresent()).isTrue();
            assertThat(response.jobDescriptionIntelligence().locationRequirements()).contains("Bengaluru");
        }

        @Test
        @DisplayName("toResponse preserves explicit description intelligence verbatim")
        void toResponse_PreservesExplicitDescriptionIntelligence() throws Exception {
            UUID id = UUID.randomUUID();
            Job job = createFullJob(id);
            com.joblivo.job.intelligence.JobDescriptionIntelligence explicitIntelligence =
                    com.joblivo.job.intelligence.JobDescriptionIntelligence.empty(id);

            JobResponse response = mapper.toResponse(
                    job,
                    JobFreshnessStatus.ACTIVE,
                    com.joblivo.job.model.JobAuthenticityAssessment.noWarning(),
                    explicitIntelligence
            );

            assertThat(response).isNotNull();
            assertThat(response.jobDescriptionIntelligence()).isSameAs(explicitIntelligence);
        }
    }
}
