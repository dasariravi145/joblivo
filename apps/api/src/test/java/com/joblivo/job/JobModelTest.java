package com.joblivo.job;

import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import com.joblivo.job.service.JobResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Job Entity & Model Unit Tests")
class JobModelTest {

    @Nested
    @DisplayName("Entity Creation & Defaults")
    class CreationTests {

        @Test
        @DisplayName("Instantiating Job with valid identity sets default enums and timestamps")
        void createsValidJob() {
            Job job = new Job(JobSource.LINKEDIN, "ext-1001", "Senior Java Engineer", "Acme Cloud");

            assertThat(job.getSource()).isEqualTo(JobSource.LINKEDIN);
            assertThat(job.getExternalJobId()).isEqualTo("ext-1001");
            assertThat(job.getTitle()).isEqualTo("Senior Java Engineer");
            assertThat(job.getCompanyName()).isEqualTo("Acme Cloud");
            assertThat(job.getWorkMode()).isEqualTo(JobWorkMode.UNKNOWN);
            assertThat(job.getEmploymentType()).isEqualTo(JobEmploymentType.UNKNOWN);
            assertThat(job.getApplicationMethod()).isEqualTo(JobApplicationMethod.UNKNOWN);
            assertThat(job.getDiscoveredAt()).isNotNull();
            assertThat(job.getLastSeenAt()).isNotNull();
        }

        @Test
        @DisplayName("Throws NullPointerException if required identity arguments are missing")
        void missingRequiredFieldsThrowsException() {
            assertThatThrownBy(() -> new Job(null, "ext-1", "Title", "Company"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("source must not be null");

            assertThatThrownBy(() -> new Job(JobSource.ATS, null, "Title", "Company"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("externalJobId must not be null");

            assertThatThrownBy(() -> new Job(JobSource.ATS, "ext-1", null, "Company"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("title must not be null");

            assertThatThrownBy(() -> new Job(JobSource.ATS, "ext-1", "Title", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("companyName must not be null");
        }
    }

    @Nested
    @DisplayName("Lifecycle Callbacks")
    class LifecycleTests {

        @Test
        @DisplayName("onCreate sets createdAt, updatedAt, discoveredAt, and lastSeenAt")
        void onCreateSetsTimestamps() {
            Job job = new Job(JobSource.NAUKRI, "ext-2002", "Backend Architect", "FinTech Solutions");
            job.onCreate();

            assertThat(job.getCreatedAt()).isNotNull();
            assertThat(job.getUpdatedAt()).isNotNull();
            assertThat(job.getDiscoveredAt()).isNotNull();
            assertThat(job.getLastSeenAt()).isNotNull();
        }

        @Test
        @DisplayName("onUpdate refreshes updatedAt")
        void onUpdateRefreshesTimestamp() throws InterruptedException {
            Job job = new Job(JobSource.COMPANY_CAREERS, "ext-3003", "Staff Engineer", "SaaS Inc");
            job.onCreate();
            Instant initialUpdated = job.getUpdatedAt();

            Thread.sleep(10);
            job.onUpdate();

            assertThat(job.getUpdatedAt()).isAfterOrEqualTo(initialUpdated);
        }
    }

    @Nested
    @DisplayName("Composite Identity & Equality")
    class EqualityTests {

        @Test
        @DisplayName("Jobs with the same source and externalJobId are considered equal before ID assignment")
        void equalityBySourceAndExternalId() {
            Job job1 = new Job(JobSource.LINKEDIN, "ext-123", "Title 1", "Company 1");
            Job job2 = new Job(JobSource.LINKEDIN, "ext-123", "Title 2", "Company 2");

            assertThat(job1).isEqualTo(job2);
            assertThat(job1.hashCode()).isEqualTo(job2.hashCode());
        }

        @Test
        @DisplayName("Jobs with different sources and same external ID are NOT equal")
        void differentSourceSameExternalIdNotEqual() {
            Job job1 = new Job(JobSource.LINKEDIN, "ext-123", "Title", "Company");
            Job job2 = new Job(JobSource.NAUKRI, "ext-123", "Title", "Company");

            assertThat(job1).isNotEqualTo(job2);
        }

        @Test
        @DisplayName("Jobs with same source and different external IDs are NOT equal")
        void sameSourceDifferentExternalIdNotEqual() {
            Job job1 = new Job(JobSource.LINKEDIN, "ext-123", "Title", "Company");
            Job job2 = new Job(JobSource.LINKEDIN, "ext-456", "Title", "Company");

            assertThat(job1).isNotEqualTo(job2);
        }
    }

    @Nested
    @DisplayName("Response DTO Projection")
    class ResponseProjectionTests {

        @Test
        @DisplayName("JobResponse accurately projects all entity fields")
        void responseProjectsAllFields() {
            Job job = new Job(JobSource.CUTSHORT, "cs-999", "DevOps Lead", "Cloud Native Lab");
            job.onCreate();
            job.setRecruiterName("Jane Doe");
            job.setDescription("Leading Kubernetes and AWS cloud operations");
            job.setLocation("Austin, TX");
            job.setWorkMode(JobWorkMode.REMOTE);
            job.setEmploymentType(JobEmploymentType.FULL_TIME);
            job.setExperienceMinYears(5);
            job.setExperienceMaxYears(8);
            job.setSalaryMin(BigDecimal.valueOf(150000));
            job.setSalaryMax(BigDecimal.valueOf(180000));
            job.setSalaryCurrency("USD");
            job.setSalaryPeriod(SalaryPeriod.YEAR);
            job.setJobUrl("https://example.com/jobs/999");
            job.setCompanyUrl("https://example.com");
            job.setApplicationMethod(JobApplicationMethod.EXTERNAL_COMPANY_SITE);

            JobResponse response = JobResponse.from(job);

            assertThat(response.source()).isEqualTo(JobSource.CUTSHORT);
            assertThat(response.externalJobId()).isEqualTo("cs-999");
            assertThat(response.title()).isEqualTo("DevOps Lead");
            assertThat(response.companyName()).isEqualTo("Cloud Native Lab");
            assertThat(response.recruiterName()).isEqualTo("Jane Doe");
            assertThat(response.location()).isEqualTo("Austin, TX");
            assertThat(response.workMode()).isEqualTo(JobWorkMode.REMOTE);
            assertThat(response.employmentType()).isEqualTo(JobEmploymentType.FULL_TIME);
            assertThat(response.experienceMinYears()).isEqualTo(5);
            assertThat(response.experienceMaxYears()).isEqualTo(8);
            assertThat(response.salaryMin()).isEqualByComparingTo(BigDecimal.valueOf(150000));
            assertThat(response.salaryMax()).isEqualByComparingTo(BigDecimal.valueOf(180000));
            assertThat(response.salaryCurrency()).isEqualTo("USD");
            assertThat(response.salaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
            assertThat(response.jobUrl()).isEqualTo("https://example.com/jobs/999");
            assertThat(response.applicationMethod()).isEqualTo(JobApplicationMethod.EXTERNAL_COMPANY_SITE);
        }
    }
}
