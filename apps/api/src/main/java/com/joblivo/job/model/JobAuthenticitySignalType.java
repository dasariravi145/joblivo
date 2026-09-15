package com.joblivo.job.model;

/**
 * Deterministic signal types identified during job authenticity and scam-risk evaluation.
 * Evaluated strictly from fields available in the canonical {@link com.joblivo.job.Job} entity
 * and canonical freshness / source provenance metadata.
 */
public enum JobAuthenticitySignalType {

    /**
     * Triggered when companyName is null, empty, or blank.
     */
    MISSING_COMPANY_INFORMATION(JobAuthenticityRiskLevel.LOW, "Company information is missing from the job record."),

    /**
     * Triggered when job application URL is null, empty, or blank.
     */
    MISSING_JOB_URL(JobAuthenticityRiskLevel.LOW, "Job application URL is missing from the job record."),

    /**
     * Triggered when applicationMethod is unsupported, unknown, or inconsistent with job data
     * (e.g. requires external destination but no URL is provided).
     */
    INVALID_OR_UNSUPPORTED_APPLICATION_METHOD(JobAuthenticityRiskLevel.MEDIUM, "Application method is inconsistent with available application details."),

    /**
     * Informational warning triggered when the job's expiration timestamp has passed according to the canonical freshness evaluator.
     */
    EXPIRED_JOB(JobAuthenticityRiskLevel.LOW, "The job's expiration time has passed."),

    /**
     * Informational warning triggered when the job exceeds the canonical staleness threshold.
     */
    STALE_JOB(JobAuthenticityRiskLevel.LOW, "The job has exceeded the staleness threshold."),

    /**
     * Triggered when job description is absent or blank.
     */
    MISSING_DESCRIPTION(JobAuthenticityRiskLevel.LOW, "Job description is missing from the job record."),

    /**
     * Triggered when location is absent or blank.
     */
    MISSING_LOCATION(JobAuthenticityRiskLevel.LOW, "Job location is missing from the job record."),

    /**
     * Triggered ONLY when salary data is internally inconsistent (e.g. minimum salary &gt; maximum salary or negative salary).
     */
    SUSPICIOUS_SALARY_DATA(JobAuthenticityRiskLevel.HIGH, "Salary range is internally inconsistent."),

    /**
     * Triggered when experience requirements are internally inconsistent (e.g. minimum experience &gt; maximum experience or negative experience).
     */
    INVALID_EXPERIENCE_RANGE(JobAuthenticityRiskLevel.HIGH, "Experience requirement range is internally inconsistent."),

    /**
     * Triggered when required source provenance (source or externalJobId) is missing or invalid.
     */
    SOURCE_PROVENANCE_UNAVAILABLE(JobAuthenticityRiskLevel.HIGH, "Job source provenance is missing or invalid."),

    /**
     * Informational warning triggered when recruiter details are absent. Does NOT imply fraud or illegitimacy.
     */
    MISSING_RECRUITER_INFORMATION(JobAuthenticityRiskLevel.LOW, "Recruiter contact information is not provided.");

    private final JobAuthenticityRiskLevel defaultRiskLevel;
    private final String defaultExplanation;

    JobAuthenticitySignalType(JobAuthenticityRiskLevel defaultRiskLevel, String defaultExplanation) {
        this.defaultRiskLevel = defaultRiskLevel;
        this.defaultExplanation = defaultExplanation;
    }

    public JobAuthenticityRiskLevel getDefaultRiskLevel() {
        return defaultRiskLevel;
    }

    public String getDefaultExplanation() {
        return defaultExplanation;
    }
}
