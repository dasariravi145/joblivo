package com.joblivo.job.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.joblivo.job.Job;
import com.joblivo.job.intelligence.JobDescriptionIntelligence;
import com.joblivo.job.intelligence.JobDescriptionParser;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobAuthenticityAssessment;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobFreshnessStatus;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobSourceIdentity;
import com.joblivo.job.model.JobSourceProvenance;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public response projection for normalized Job records.
 * Exposes factual job details without persistence internals.
 * Includes derived {@link #freshnessStatus()} indicating data freshness (ACTIVE, EXPIRED, STALE, UNKNOWN),
 * derived {@link #authenticityAssessment()} indicating deterministic authenticity and scam warning signals,
 * and derived {@link #jobDescriptionIntelligence()} indicating structured job description intelligence.
 */
public record JobResponse(
        UUID id,
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
        JobApplicationMethod applicationMethod,
        Instant createdAt,
        Instant updatedAt,
        JobFreshnessStatus freshnessStatus,
        JobAuthenticityAssessment authenticityAssessment,
        JobDescriptionIntelligence jobDescriptionIntelligence
) {

    private static final JobDescriptionParser DEFAULT_PARSER = new JobDescriptionParser();

    public JobResponse {
        if (freshnessStatus == null) {
            freshnessStatus = JobFreshnessStatus.UNKNOWN;
        }
        if (authenticityAssessment == null) {
            authenticityAssessment = JobAuthenticityAssessment.noWarning();
        }
        if (jobDescriptionIntelligence == null) {
            jobDescriptionIntelligence = JobDescriptionIntelligence.empty(id);
        }
    }

    /**
     * Backward-compatible 27-parameter constructor accepting explicit {@link JobFreshnessStatus}
     * and {@link JobAuthenticityAssessment}, defaulting {@link JobDescriptionIntelligence}.
     */
    public JobResponse(
            UUID id,
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
            JobApplicationMethod applicationMethod,
            Instant createdAt,
            Instant updatedAt,
            JobFreshnessStatus freshnessStatus,
            JobAuthenticityAssessment authenticityAssessment
    ) {
        this(
                id, source, externalJobId, title, companyName, recruiterName, description, location,
                workMode, employmentType, experienceMinYears, experienceMaxYears, salaryMin, salaryMax,
                salaryCurrency, salaryPeriod, jobUrl, companyUrl, postedAt, expiresAt, discoveredAt,
                lastSeenAt, applicationMethod, createdAt, updatedAt, freshnessStatus,
                authenticityAssessment, JobDescriptionIntelligence.empty(id)
        );
    }

    /**
     * Backward-compatible 26-parameter constructor accepting explicit {@link JobFreshnessStatus}
     * and defaulting assessment and description intelligence.
     */
    public JobResponse(
            UUID id,
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
            JobApplicationMethod applicationMethod,
            Instant createdAt,
            Instant updatedAt,
            JobFreshnessStatus freshnessStatus
    ) {
        this(
                id, source, externalJobId, title, companyName, recruiterName, description, location,
                workMode, employmentType, experienceMinYears, experienceMaxYears, salaryMin, salaryMax,
                salaryCurrency, salaryPeriod, jobUrl, companyUrl, postedAt, expiresAt, discoveredAt,
                lastSeenAt, applicationMethod, createdAt, updatedAt, freshnessStatus,
                JobAuthenticityAssessment.noWarning(), JobDescriptionIntelligence.empty(id)
        );
    }

    /**
     * Backward-compatible 25-parameter constructor deriving defaults.
     */
    public JobResponse(
            UUID id,
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
            JobApplicationMethod applicationMethod,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id, source, externalJobId, title, companyName, recruiterName, description, location,
                workMode, employmentType, experienceMinYears, experienceMaxYears, salaryMin, salaryMax,
                salaryCurrency, salaryPeriod, jobUrl, companyUrl, postedAt, expiresAt, discoveredAt,
                lastSeenAt, applicationMethod, createdAt, updatedAt,
                JobFreshnessEvaluator.evaluateStatic(postedAt, expiresAt, discoveredAt, lastSeenAt),
                JobAuthenticityAssessment.noWarning(), JobDescriptionIntelligence.empty(id)
        );
    }

    /**
     * Creates a {@link JobResponse} projection from a {@link Job} entity with explicit parameters.
     */
    public static JobResponse from(
            Job job,
            JobFreshnessStatus freshnessStatus,
            JobAuthenticityAssessment authenticityAssessment,
            JobDescriptionIntelligence jobDescriptionIntelligence
    ) {
        return new JobResponse(
                job.getId(),
                job.getSource(),
                job.getExternalJobId(),
                job.getTitle(),
                job.getCompanyName(),
                job.getRecruiterName(),
                job.getDescription(),
                job.getLocation(),
                job.getWorkMode(),
                job.getEmploymentType(),
                job.getExperienceMinYears(),
                job.getExperienceMaxYears(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getSalaryPeriod(),
                job.getJobUrl(),
                job.getCompanyUrl(),
                job.getPostedAt(),
                job.getExpiresAt(),
                job.getDiscoveredAt(),
                job.getLastSeenAt(),
                job.getApplicationMethod(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                freshnessStatus,
                authenticityAssessment,
                jobDescriptionIntelligence
        );
    }

    /**
     * Creates a {@link JobResponse} projection from a {@link Job} entity with explicit freshness and authenticity,
     * deriving job description intelligence.
     */
    public static JobResponse from(Job job, JobFreshnessStatus freshnessStatus, JobAuthenticityAssessment authenticityAssessment) {
        return from(job, freshnessStatus, authenticityAssessment, DEFAULT_PARSER.parse(job));
    }

    /**
     * Creates a {@link JobResponse} projection from a {@link Job} entity with an explicitly evaluated freshness status,
     * deriving authenticity assessment and description intelligence deterministically.
     */
    public static JobResponse from(Job job, JobFreshnessStatus freshnessStatus) {
        JobAuthenticityAssessment authenticity = JobAuthenticityEvaluator.evaluateStatic(job, freshnessStatus);
        return from(job, freshnessStatus, authenticity, DEFAULT_PARSER.parse(job));
    }

    /**
     * Creates a {@link JobResponse} projection from a {@link Job} entity, evaluating freshness, authenticity,
     * and description intelligence via default canonical evaluators.
     */
    public static JobResponse from(Job job) {
        JobFreshnessStatus status = JobFreshnessEvaluator.evaluateStatic(job);
        JobAuthenticityAssessment authenticity = JobAuthenticityEvaluator.evaluateStatic(job, status);
        return from(job, status, authenticity, DEFAULT_PARSER.parse(job));
    }

    /**
     * Resolves the canonical, immutable {@link JobSourceIdentity} for this job read model.
     *
     * @return canonical JobSourceIdentity
     */
    @JsonIgnore
    public JobSourceIdentity sourceIdentity() {
        return new JobSourceIdentity(source, externalJobId);
    }

    /**
     * Resolves the canonical, immutable {@link JobSourceProvenance} for this job read model.
     *
     * @return canonical JobSourceProvenance
     */
    @JsonIgnore
    public JobSourceProvenance sourceProvenance() {
        return new JobSourceProvenance(
                source,
                externalJobId,
                jobUrl,
                companyUrl,
                postedAt,
                expiresAt,
                discoveredAt,
                lastSeenAt,
                applicationMethod
        );
    }
}
