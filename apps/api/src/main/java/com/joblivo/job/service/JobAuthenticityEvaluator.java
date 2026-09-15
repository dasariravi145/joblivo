package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobAuthenticityAssessment;
import com.joblivo.job.model.JobAuthenticityRiskLevel;
import com.joblivo.job.model.JobAuthenticitySignal;
import com.joblivo.job.model.JobAuthenticitySignalType;
import com.joblivo.job.model.JobFreshnessStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic canonical evaluator for Job Authenticity and Scam-Risk Signals.
 * <p>
 * Evaluates warning signals strictly from fields already present in the canonical {@link Job} entity,
 * source provenance, and freshness lifecycle metadata.
 * <p>
 * <strong>Guiding Principles:</strong>
 * <ul>
 *     <li><strong>No Probabilistic Scoring:</strong> Never outputs arbitrary percentages (e.g. "97% authentic" or "3% scam").</li>
 *     <li><strong>Transparent Determinism:</strong> Every warning signal includes an explicit deterministic signal type,
 *         risk level (NONE, LOW, MEDIUM, HIGH), human-readable explanation, and technical trigger reason.</li>
 *     <li><strong>No Source Bias:</strong> Does not assign trust or reputation scores to specific sources (e.g. LinkedIn vs Naukri).</li>
 *     <li><strong>Reuses Existing Freshness:</strong> Delegates freshness evaluation to the canonical {@link JobFreshnessEvaluator},
 *         never duplicating timestamp rules.</li>
 *     <li><strong>Decoupled from Duplication:</strong> Does not treat duplicate or reposted jobs as fraudulent.</li>
 *     <li><strong>Strictly Read-Only:</strong> Performs no entity mutations, database updates, network calls, or AI provider invocations.</li>
 * </ul>
 */
@Component
public class JobAuthenticityEvaluator {

    private static final JobAuthenticityEvaluator DEFAULT_INSTANCE = new JobAuthenticityEvaluator();

    private final JobFreshnessEvaluator freshnessEvaluator;

    @Autowired
    public JobAuthenticityEvaluator(JobFreshnessEvaluator freshnessEvaluator) {
        this.freshnessEvaluator = Objects.requireNonNullElseGet(freshnessEvaluator, JobFreshnessEvaluator::new);
    }

    public JobAuthenticityEvaluator() {
        this(new JobFreshnessEvaluator());
    }

    /**
     * Evaluates deterministic authenticity signals for a {@link Job}, delegating freshness to the canonical evaluator.
     *
     * @param job the job entity to evaluate (may be null)
     * @return deterministic {@link JobAuthenticityAssessment}
     */
    public JobAuthenticityAssessment evaluate(Job job) {
        if (job == null) {
            return new JobAuthenticityAssessment(
                    com.joblivo.job.model.JobAuthenticityOutcome.INSUFFICIENT_EVIDENCE,
                    JobAuthenticityRiskLevel.NONE,
                    List.of(),
                    true
            );
        }
        JobFreshnessStatus status = freshnessEvaluator.evaluate(job);
        return evaluate(job, status);
    }

    /**
     * Evaluates deterministic authenticity signals for a {@link Job} with an explicitly provided freshness status.
     *
     * @param job             the job entity to evaluate
     * @param freshnessStatus pre-evaluated freshness status
     * @return deterministic {@link JobAuthenticityAssessment}
     */
    public JobAuthenticityAssessment evaluate(Job job, JobFreshnessStatus freshnessStatus) {
        if (job == null) {
            return new JobAuthenticityAssessment(
                    com.joblivo.job.model.JobAuthenticityOutcome.INSUFFICIENT_EVIDENCE,
                    JobAuthenticityRiskLevel.NONE,
                    List.of(),
                    true
            );
        }

        List<JobAuthenticitySignal> signals = new ArrayList<>();

        // 1. MISSING_COMPANY_INFORMATION
        if (isBlank(job.getCompanyName())) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.MISSING_COMPANY_INFORMATION,
                    "companyName is null or blank"
            ));
        }

        // 2. MISSING_JOB_URL
        if (isBlank(job.getJobUrl())) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.MISSING_JOB_URL,
                    "jobUrl is null or blank"
            ));
        }

        // 3. INVALID_OR_UNSUPPORTED_APPLICATION_METHOD
        JobApplicationMethod applicationMethod = job.getApplicationMethod();
        if (applicationMethod == null || applicationMethod == JobApplicationMethod.UNKNOWN) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.INVALID_OR_UNSUPPORTED_APPLICATION_METHOD,
                    "Application method is UNKNOWN or unspecified"
            ));
        } else if (requiresExternalUrl(applicationMethod) && isBlank(job.getJobUrl())) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.INVALID_OR_UNSUPPORTED_APPLICATION_METHOD,
                    "Application method (" + applicationMethod + ") requires an external destination URL which is missing"
            ));
        }

        // 4. EXPIRED_JOB & 5. STALE_JOB (reusing canonical freshness evaluator)
        if (freshnessStatus == JobFreshnessStatus.EXPIRED) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.EXPIRED_JOB,
                    "freshnessStatus evaluated to EXPIRED from job expiration timestamps"
            ));
        } else if (freshnessStatus == JobFreshnessStatus.STALE) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.STALE_JOB,
                    "freshnessStatus evaluated to STALE exceeding staleness threshold"
            ));
        }

        // 6. MISSING_DESCRIPTION
        if (isBlank(job.getDescription())) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.MISSING_DESCRIPTION,
                    "description is null or blank"
            ));
        }

        // 7. MISSING_LOCATION
        if (isBlank(job.getLocation())) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.MISSING_LOCATION,
                    "location is null or blank"
            ));
        }

        // 8. SUSPICIOUS_SALARY_DATA
        // Trigger ONLY for objectively invalid/inconsistent salary data.
        // Never trigger merely because a salary is high.
        BigDecimal salaryMin = job.getSalaryMin();
        BigDecimal salaryMax = job.getSalaryMax();
        if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.SUSPICIOUS_SALARY_DATA,
                    "salaryMin (" + salaryMin + ") is greater than salaryMax (" + salaryMax + ")"
            ));
        } else if ((salaryMin != null && salaryMin.compareTo(BigDecimal.ZERO) < 0)
                || (salaryMax != null && salaryMax.compareTo(BigDecimal.ZERO) < 0)) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.SUSPICIOUS_SALARY_DATA,
                    "salaryMin or salaryMax cannot be negative"
            ));
        } else if ((salaryMin != null || salaryMax != null) && job.getSalaryPeriod() == null) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.SUSPICIOUS_SALARY_DATA,
                    "salary bounds are provided without a valid salary period"
            ));
        }

        // 9. INVALID_EXPERIENCE_RANGE
        Integer expMin = job.getExperienceMinYears();
        Integer expMax = job.getExperienceMaxYears();
        if (expMin != null && expMax != null && expMin > expMax) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.INVALID_EXPERIENCE_RANGE,
                    "experienceMinYears (" + expMin + ") is greater than experienceMaxYears (" + expMax + ")"
            ));
        } else if ((expMin != null && expMin < 0) || (expMax != null && expMax < 0)) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.INVALID_EXPERIENCE_RANGE,
                    "experience requirement cannot be negative"
            ));
        }

        // 10. SOURCE_PROVENANCE_UNAVAILABLE
        if (job.getSource() == null || isBlank(job.getExternalJobId())) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.SOURCE_PROVENANCE_UNAVAILABLE,
                    "Required source identity (source or externalJobId) is missing or invalid"
            ));
        }

        // 11. MISSING_RECRUITER_INFORMATION
        // Informational only (LOW risk). Does NOT imply fraud or suspicion.
        if (isBlank(job.getRecruiterName())) {
            signals.add(JobAuthenticitySignal.of(
                    JobAuthenticitySignalType.MISSING_RECRUITER_INFORMATION,
                    "recruiterName is not provided in the job record"
            ));
        }

        boolean insufficientEvidence = isEvidenceInsufficient(job);
        return JobAuthenticityAssessment.fromSignals(signals, insufficientEvidence);
    }

    /**
     * Convenience static evaluation method using default evaluator instances.
     */
    public static JobAuthenticityAssessment evaluateStatic(Job job) {
        return DEFAULT_INSTANCE.evaluate(job);
    }

    /**
     * Convenience static evaluation method using default evaluator instances with explicit freshness.
     */
    public static JobAuthenticityAssessment evaluateStatic(Job job, JobFreshnessStatus freshnessStatus) {
        return DEFAULT_INSTANCE.evaluate(job, freshnessStatus);
    }

    private static boolean requiresExternalUrl(JobApplicationMethod method) {
        return method == JobApplicationMethod.EXTERNAL_COMPANY_SITE
                || method == JobApplicationMethod.ATS
                || method == JobApplicationMethod.SOURCE_PORTAL;
    }

    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    private static boolean isEvidenceInsufficient(Job job) {
        if (job == null) {
            return true;
        }
        if (job.getSource() == null || isBlank(job.getExternalJobId()) || isBlank(job.getTitle())) {
            return true;
        }
        if (isBlank(job.getCompanyName()) && isBlank(job.getJobUrl()) && isBlank(job.getDescription())) {
            return true;
        }
        return false;
    }

    public JobFreshnessEvaluator getFreshnessEvaluator() {
        return freshnessEvaluator;
    }
}
