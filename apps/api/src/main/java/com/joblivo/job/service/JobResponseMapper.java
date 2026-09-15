package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.intelligence.JobDescriptionIntelligence;
import com.joblivo.job.intelligence.JobDescriptionParser;
import com.joblivo.job.model.JobAuthenticityAssessment;
import com.joblivo.job.model.JobFreshnessStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Canonical mapping boundary from the persistence entity {@link Job} to the immutable read model {@link JobResponse}.
 * <p>
 * <strong>Guarantees &amp; Invariants:</strong>
 * <ul>
 *     <li><strong>Entity Decoupling:</strong> Persistence entities (JPA/Hibernate annotations, database-level details)
 *         are never serialized or exposed directly to API callers.</li>
 *     <li><strong>Factual Field Integrity:</strong> Nullable optional fields (e.g. salary bounds, recruiter name,
 *         expiration timestamp) remain strictly {@code null} without fabricated defaults or artificial placeholders
 *         (such as "Unknown Company", "Not specified", "Remote", or "₹0").</li>
 *     <li><strong>Source Identity Preservation:</strong> Source metadata ({@code externalJobId}, {@code source},
 *         {@code jobUrl}, {@code companyUrl}) is preserved exactly as normalized during ingestion without rewriting
 *         or artificial generation.</li>
 *     <li><strong>Canonical Freshness Delegation:</strong> Freshness evaluation delegates exclusively to the canonical
 *         {@link JobFreshnessEvaluator}, preventing duplicated timestamp rules.</li>
 *     <li><strong>Canonical Authenticity Delegation:</strong> Authenticity and scam-risk evaluation delegates exclusively
 *         to the canonical {@link JobAuthenticityEvaluator}, providing transparent warning signals without arbitrary percentages.</li>
 *     <li><strong>Canonical Intelligence Delegation:</strong> Job description intelligence extraction delegates exclusively
 *         to the deterministic {@link JobDescriptionParser}, extracting source-grounded signals without AI or hallucinations.</li>
 *     <li><strong>Read-Only Data Integrity:</strong> The mapping operation is strictly side-effect free and never mutates
 *         stored entity timestamps ({@code postedAt}, {@code expiresAt}, {@code discoveredAt}, {@code lastSeenAt})
 *         or database records.</li>
 * </ul>
 */
@Component
public class JobResponseMapper {

    private final JobFreshnessEvaluator freshnessEvaluator;
    private final JobAuthenticityEvaluator authenticityEvaluator;
    private final JobDescriptionParser descriptionParser;

    @Autowired
    public JobResponseMapper(
            JobFreshnessEvaluator freshnessEvaluator,
            JobAuthenticityEvaluator authenticityEvaluator,
            JobDescriptionParser descriptionParser
    ) {
        this.freshnessEvaluator = Objects.requireNonNullElseGet(freshnessEvaluator, JobFreshnessEvaluator::new);
        this.authenticityEvaluator = Objects.requireNonNullElseGet(
                authenticityEvaluator,
                () -> new JobAuthenticityEvaluator(this.freshnessEvaluator)
        );
        this.descriptionParser = Objects.requireNonNullElseGet(descriptionParser, JobDescriptionParser::new);
    }

    public JobResponseMapper(JobFreshnessEvaluator freshnessEvaluator, JobAuthenticityEvaluator authenticityEvaluator) {
        this(freshnessEvaluator, authenticityEvaluator, new JobDescriptionParser());
    }

    public JobResponseMapper(JobFreshnessEvaluator freshnessEvaluator) {
        this(freshnessEvaluator, new JobAuthenticityEvaluator(freshnessEvaluator), new JobDescriptionParser());
    }

    public JobResponseMapper() {
        this(new JobFreshnessEvaluator(), new JobAuthenticityEvaluator(), new JobDescriptionParser());
    }

    /**
     * Maps a {@link Job} persistence entity to the canonical immutable {@link JobResponse} read representation,
     * evaluating freshness, authenticity, and description intelligence deterministically through canonical evaluators.
     *
     * @param job the persistence entity (may be null)
     * @return canonical immutable read model, or {@code null} if job is null
     */
    public JobResponse toResponse(Job job) {
        if (job == null) {
            return null;
        }
        JobFreshnessStatus status = freshnessEvaluator.evaluate(job);
        JobAuthenticityAssessment authenticity = authenticityEvaluator.evaluate(job, status);
        JobDescriptionIntelligence intelligence = descriptionParser.parse(job);
        return toResponse(job, status, authenticity, intelligence);
    }

    /**
     * Maps a {@link Job} persistence entity to the canonical immutable {@link JobResponse} read representation
     * with an explicitly evaluated {@link JobFreshnessStatus}.
     *
     * @param job             the persistence entity (may be null)
     * @param freshnessStatus the explicitly evaluated freshness status
     * @return canonical immutable read model, or {@code null} if job is null
     */
    public JobResponse toResponse(Job job, JobFreshnessStatus freshnessStatus) {
        if (job == null) {
            return null;
        }
        JobAuthenticityAssessment authenticity = authenticityEvaluator.evaluate(job, freshnessStatus);
        JobDescriptionIntelligence intelligence = descriptionParser.parse(job);
        return toResponse(job, freshnessStatus, authenticity, intelligence);
    }

    /**
     * Maps a {@link Job} persistence entity to the canonical immutable {@link JobResponse} read representation
     * with an explicitly evaluated {@link JobFreshnessStatus} and {@link JobAuthenticityAssessment}.
     *
     * @param job                    the persistence entity (may be null)
     * @param freshnessStatus        the explicitly evaluated freshness status
     * @param authenticityAssessment the explicitly evaluated authenticity assessment
     * @return canonical immutable read model, or {@code null} if job is null
     */
    public JobResponse toResponse(Job job, JobFreshnessStatus freshnessStatus, JobAuthenticityAssessment authenticityAssessment) {
        if (job == null) {
            return null;
        }
        JobDescriptionIntelligence intelligence = descriptionParser.parse(job);
        return toResponse(job, freshnessStatus, authenticityAssessment, intelligence);
    }

    /**
     * Maps a {@link Job} persistence entity to the canonical immutable {@link JobResponse} read representation
     * with explicitly evaluated {@link JobFreshnessStatus}, {@link JobAuthenticityAssessment}, and {@link JobDescriptionIntelligence}.
     *
     * @param job                        the persistence entity (may be null)
     * @param freshnessStatus            the explicitly evaluated freshness status
     * @param authenticityAssessment     the explicitly evaluated authenticity assessment
     * @param jobDescriptionIntelligence the explicitly evaluated description intelligence
     * @return canonical immutable read model, or {@code null} if job is null
     */
    public JobResponse toResponse(
            Job job,
            JobFreshnessStatus freshnessStatus,
            JobAuthenticityAssessment authenticityAssessment,
            JobDescriptionIntelligence jobDescriptionIntelligence
    ) {
        if (job == null) {
            return null;
        }
        return JobResponse.from(job, freshnessStatus, authenticityAssessment, jobDescriptionIntelligence);
    }

    public JobFreshnessEvaluator getFreshnessEvaluator() {
        return freshnessEvaluator;
    }

    public JobAuthenticityEvaluator getAuthenticityEvaluator() {
        return authenticityEvaluator;
    }

    public JobDescriptionParser getDescriptionParser() {
        return descriptionParser;
    }
}
