package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Source-neutral immutable data carrier representing an ingested or discovered job posting.
 * Translates source-specific data into normalized, platform-standard attributes before persistence.
 */
public record JobCandidate(
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
    public JobCandidate {
        Objects.requireNonNull(source, "Job source must not be null");
        if (externalJobId == null || externalJobId.isBlank()) {
            throw new IllegalArgumentException("externalJobId must not be null or blank");
        }
        externalJobId = externalJobId.trim();

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be null or blank");
        }
        title = title.trim();

        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName must not be null or blank");
        }
        companyName = companyName.trim();

        recruiterName = recruiterName != null && !recruiterName.isBlank() ? recruiterName.trim() : null;
        description = description != null && !description.isBlank() ? description.trim() : null;
        location = location != null && !location.isBlank() ? location.trim() : null;

        workMode = workMode != null ? workMode : JobWorkMode.UNKNOWN;
        employmentType = employmentType != null ? employmentType : JobEmploymentType.UNKNOWN;
        applicationMethod = applicationMethod != null ? applicationMethod : JobApplicationMethod.UNKNOWN;

        salaryCurrency = salaryCurrency != null && !salaryCurrency.isBlank() ? salaryCurrency.trim().toUpperCase() : null;
        jobUrl = jobUrl != null && !jobUrl.isBlank() ? jobUrl.trim() : null;
        companyUrl = companyUrl != null && !companyUrl.isBlank() ? companyUrl.trim() : null;

        discoveredAt = discoveredAt != null ? discoveredAt : Instant.now();
        lastSeenAt = lastSeenAt != null ? lastSeenAt : discoveredAt;
    }

    public JobIngestionCandidate toIngestionCandidate() {
        return JobIngestionCandidate.from(this);
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

        public JobCandidate build() {
            return new JobCandidate(
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
