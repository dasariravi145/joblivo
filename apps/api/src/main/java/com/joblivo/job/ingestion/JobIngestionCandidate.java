package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobSourceIdentity;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Internal immutable ingestion data model representing a raw or candidate job received from a source adapter.
 * Contains the raw source fields prior to domain normalization and database persistence.
 */
public record JobIngestionCandidate(
        JobSource source,
        String externalJobId,
        String title,
        String companyName,
        String recruiterName,
        String description,
        String location,
        JobWorkMode workMode,
        String rawWorkMode,
        JobEmploymentType employmentType,
        String rawEmploymentType,
        Integer experienceMinYears,
        Integer experienceMaxYears,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        SalaryPeriod salaryPeriod,
        String rawSalaryPeriod,
        String jobUrl,
        String companyUrl,
        Instant postedAt,
        Instant expiresAt,
        Instant discoveredAt,
        Instant lastSeenAt,
        JobApplicationMethod applicationMethod,
        String rawApplicationMethod
) {

    public JobIngestionCandidate {
        Objects.requireNonNull(source, "Job source must not be null");
    }

    /**
     * Safely resolves the canonical {@link JobSourceIdentity} if both source and externalJobId are valid.
     *
     * @return Optional containing the canonical source identity, or empty if externalJobId is absent or blank
     */
    public Optional<JobSourceIdentity> getSourceIdentity() {
        return JobSourceIdentity.of(source, externalJobId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JobIngestionCandidate from(JobCandidate candidate) {
        Objects.requireNonNull(candidate, "JobCandidate must not be null");
        return builder()
                .source(candidate.source())
                .externalJobId(candidate.externalJobId())
                .title(candidate.title())
                .companyName(candidate.companyName())
                .recruiterName(candidate.recruiterName())
                .description(candidate.description())
                .location(candidate.location())
                .workMode(candidate.workMode())
                .employmentType(candidate.employmentType())
                .experienceMinYears(candidate.experienceMinYears())
                .experienceMaxYears(candidate.experienceMaxYears())
                .salaryMin(candidate.salaryMin())
                .salaryMax(candidate.salaryMax())
                .salaryCurrency(candidate.salaryCurrency())
                .salaryPeriod(candidate.salaryPeriod())
                .jobUrl(candidate.jobUrl())
                .companyUrl(candidate.companyUrl())
                .postedAt(candidate.postedAt())
                .expiresAt(candidate.expiresAt())
                .discoveredAt(candidate.discoveredAt())
                .lastSeenAt(candidate.lastSeenAt())
                .applicationMethod(candidate.applicationMethod())
                .build();
    }

    public static class Builder {
        private JobSource source;
        private String externalJobId;
        private String title;
        private String companyName;
        private String recruiterName;
        private String description;
        private String location;
        private JobWorkMode workMode;
        private String rawWorkMode;
        private JobEmploymentType employmentType;
        private String rawEmploymentType;
        private Integer experienceMinYears;
        private Integer experienceMaxYears;
        private BigDecimal salaryMin;
        private BigDecimal salaryMax;
        private String salaryCurrency;
        private SalaryPeriod salaryPeriod;
        private String rawSalaryPeriod;
        private String jobUrl;
        private String companyUrl;
        private Instant postedAt;
        private Instant expiresAt;
        private Instant discoveredAt;
        private Instant lastSeenAt;
        private JobApplicationMethod applicationMethod;
        private String rawApplicationMethod;

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

        public Builder rawWorkMode(String rawWorkMode) {
            this.rawWorkMode = rawWorkMode;
            return this;
        }

        public Builder employmentType(JobEmploymentType employmentType) {
            this.employmentType = employmentType;
            return this;
        }

        public Builder rawEmploymentType(String rawEmploymentType) {
            this.rawEmploymentType = rawEmploymentType;
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

        public Builder experienceRange(Integer min, Integer max) {
            this.experienceMinYears = min;
            this.experienceMaxYears = max;
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

        public Builder salaryRange(BigDecimal min, BigDecimal max, String currency, SalaryPeriod period) {
            this.salaryMin = min;
            this.salaryMax = max;
            this.salaryCurrency = currency;
            this.salaryPeriod = period;
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

        public Builder rawSalaryPeriod(String rawSalaryPeriod) {
            this.rawSalaryPeriod = rawSalaryPeriod;
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

        public Builder rawApplicationMethod(String rawApplicationMethod) {
            this.rawApplicationMethod = rawApplicationMethod;
            return this;
        }

        public JobIngestionCandidate build() {
            return new JobIngestionCandidate(
                    source, externalJobId, title, companyName, recruiterName,
                    description, location, workMode, rawWorkMode,
                    employmentType, rawEmploymentType,
                    experienceMinYears, experienceMaxYears,
                    salaryMin, salaryMax, salaryCurrency, salaryPeriod, rawSalaryPeriod,
                    jobUrl, companyUrl, postedAt, expiresAt, discoveredAt, lastSeenAt,
                    applicationMethod, rawApplicationMethod
            );
        }
    }
}
