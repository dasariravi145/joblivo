package com.joblivo.job.normalizer;

import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value record representing clean, normalized, and validated job data ready for persistence.
 */
public record NormalizedJobCandidate(
        JobSource source,
        String externalJobId,
        String title,
        String companyName,
        String recruiterName,
        String description,
        String location,
        JobWorkMode workMode,
        JobEmploymentType employmentType,
        Integer experienceMinYears,
        Integer experienceMaxYears,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        SalaryPeriod salaryPeriod,
        String jobUrl,
        String companyUrl,
        Instant postedAt,
        Instant expiresAt,
        Instant discoveredAt,
        Instant lastSeenAt,
        JobApplicationMethod applicationMethod
) {
    public NormalizedJobCandidate {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(companyName, "companyName must not be null");
        Objects.requireNonNull(workMode, "workMode must not be null");
        Objects.requireNonNull(employmentType, "employmentType must not be null");
        Objects.requireNonNull(applicationMethod, "applicationMethod must not be null");
    }

    /**
     * Returns the canonical, immutable source identity value object.
     *
     * @return canonical JobSourceIdentity
     */
    public com.joblivo.job.model.JobSourceIdentity sourceIdentity() {
        return new com.joblivo.job.model.JobSourceIdentity(source, externalJobId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private JobSource source;
        private String externalJobId;
        private String title;
        private String companyName;
        private String recruiterName;
        private String description;
        private String location;
        private JobWorkMode workMode = JobWorkMode.UNKNOWN;
        private JobEmploymentType employmentType = JobEmploymentType.UNKNOWN;
        private Integer experienceMinYears;
        private Integer experienceMaxYears;
        private BigDecimal salaryMin;
        private BigDecimal salaryMax;
        private String salaryCurrency;
        private SalaryPeriod salaryPeriod;
        private String jobUrl;
        private String companyUrl;
        private Instant postedAt;
        private Instant expiresAt;
        private Instant discoveredAt;
        private Instant lastSeenAt;
        private JobApplicationMethod applicationMethod = JobApplicationMethod.UNKNOWN;

        public Builder source(JobSource source) {
            this.source = source;
            return this;
        }

        public Builder externalJobId(String externalJobId) {
            this.externalJobId = externalJobId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder companyName(String companyName) {
            this.companyName = companyName;
            return this;
        }

        public Builder recruiterName(String recruiterName) {
            this.recruiterName = recruiterName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder workMode(JobWorkMode workMode) {
            this.workMode = workMode;
            return this;
        }

        public Builder employmentType(JobEmploymentType employmentType) {
            this.employmentType = employmentType;
            return this;
        }

        public Builder experienceMinYears(Integer experienceMinYears) {
            this.experienceMinYears = experienceMinYears;
            return this;
        }

        public Builder experienceMaxYears(Integer experienceMaxYears) {
            this.experienceMaxYears = experienceMaxYears;
            return this;
        }

        public Builder salaryMin(BigDecimal salaryMin) {
            this.salaryMin = salaryMin;
            return this;
        }

        public Builder salaryMax(BigDecimal salaryMax) {
            this.salaryMax = salaryMax;
            return this;
        }

        public Builder salaryCurrency(String salaryCurrency) {
            this.salaryCurrency = salaryCurrency;
            return this;
        }

        public Builder salaryPeriod(SalaryPeriod salaryPeriod) {
            this.salaryPeriod = salaryPeriod;
            return this;
        }

        public Builder jobUrl(String jobUrl) {
            this.jobUrl = jobUrl;
            return this;
        }

        public Builder companyUrl(String companyUrl) {
            this.companyUrl = companyUrl;
            return this;
        }

        public Builder postedAt(Instant postedAt) {
            this.postedAt = postedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder discoveredAt(Instant discoveredAt) {
            this.discoveredAt = discoveredAt;
            return this;
        }

        public Builder lastSeenAt(Instant lastSeenAt) {
            this.lastSeenAt = lastSeenAt;
            return this;
        }

        public Builder applicationMethod(JobApplicationMethod applicationMethod) {
            this.applicationMethod = applicationMethod;
            return this;
        }

        public NormalizedJobCandidate build() {
            return new NormalizedJobCandidate(
                    source, externalJobId, title, companyName, recruiterName,
                    description, location, workMode, employmentType,
                    experienceMinYears, experienceMaxYears,
                    salaryMin, salaryMax, salaryCurrency, salaryPeriod,
                    jobUrl, companyUrl, postedAt, expiresAt, discoveredAt, lastSeenAt,
                    applicationMethod
            );
        }
    }
}
