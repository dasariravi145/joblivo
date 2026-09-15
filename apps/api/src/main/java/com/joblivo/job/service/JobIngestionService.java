package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.JobRepository;
import com.joblivo.job.duplicate.JobDuplicateClassification;
import com.joblivo.job.duplicate.JobDuplicateDetector;
import com.joblivo.job.duplicate.JobDuplicateResult;
import com.joblivo.job.duplicate.StoredJobDuplicateMatch;
import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.ingestion.CandidateIngestionAction;
import com.joblivo.job.ingestion.JobCandidate;
import com.joblivo.job.ingestion.JobCandidateIngestionResult;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.normalizer.DefaultJobNormalizer;
import com.joblivo.job.normalizer.JobNormalizer;
import com.joblivo.job.normalizer.NormalizedJobCandidate;
import com.joblivo.job.validation.JobIngestionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Core internal service responsible for normalizing, validating, and persisting job candidates.
 * <p>
 * Ensures:
 * <ul>
 *   <li>Separation of concerns: normalizes candidate data via {@link JobNormalizer} and validates via {@link JobIngestionValidator} prior to persistence.</li>
 *   <li>Source identity immutability: {@code (source, externalJobId)} uniquely defines a job.</li>
 *   <li>Idempotent ingestion: repeated ingestion updates mutable attributes and refreshes {@code lastSeenAt}.</li>
 *   <li>Data loss protection: does not accidentally erase existing stored data when source payloads provide null/unknown values.</li>
 *   <li>Zero user coupling: jobs are platform catalog data, never bound to a specific user.</li>
 * </ul>
 */
@Service
@Transactional
public class JobIngestionService {

    private static final Logger log = LoggerFactory.getLogger(JobIngestionService.class);

    private final JobRepository jobRepository;
    private final JobNormalizer jobNormalizer;
    private final JobIngestionValidator jobIngestionValidator;
    private final JobDuplicateDetector jobDuplicateDetector;

    public JobIngestionService(JobRepository jobRepository) {
        this(jobRepository, new DefaultJobNormalizer(), new JobIngestionValidator(), new JobDuplicateDetector(jobRepository));
    }

    public JobIngestionService(JobRepository jobRepository, JobNormalizer jobNormalizer) {
        this(jobRepository, jobNormalizer, new JobIngestionValidator(), new JobDuplicateDetector(jobRepository));
    }

    public JobIngestionService(
            JobRepository jobRepository,
            JobNormalizer jobNormalizer,
            JobIngestionValidator jobIngestionValidator
    ) {
        this(jobRepository, jobNormalizer, jobIngestionValidator, new JobDuplicateDetector(jobRepository));
    }

    @Autowired
    public JobIngestionService(
            JobRepository jobRepository,
            JobNormalizer jobNormalizer,
            JobIngestionValidator jobIngestionValidator,
            JobDuplicateDetector jobDuplicateDetector
    ) {
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository must not be null");
        this.jobNormalizer = Objects.requireNonNull(jobNormalizer, "jobNormalizer must not be null");
        this.jobIngestionValidator = Objects.requireNonNull(jobIngestionValidator, "jobIngestionValidator must not be null");
        this.jobDuplicateDetector = Objects.requireNonNull(jobDuplicateDetector, "jobDuplicateDetector must not be null");
    }

    /**
     * Ingests a raw candidate into the shared job catalog with candidate-level transactional isolation.
     * Returns both the persisted JobResponse and the action taken (CREATED or UPDATED).
     *
     * @param candidate raw candidate to ingest
     * @return outcome record with JobResponse and action
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobCandidateIngestionResult ingestCandidate(JobIngestionCandidate candidate) {
        Objects.requireNonNull(candidate, "JobIngestionCandidate must not be null");
        NormalizedJobCandidate normalized = jobNormalizer.normalize(candidate);
        jobIngestionValidator.validateOrThrow(normalized);
        return persistNormalized(normalized);
    }

    /**
     * Ingests a standard job candidate into the shared job catalog with candidate-level transactional isolation.
     * Returns both the persisted JobResponse and the action taken (CREATED or UPDATED).
     *
     * @param candidate job candidate to ingest
     * @return outcome record with JobResponse and action
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobCandidateIngestionResult ingestCandidate(JobCandidate candidate) {
        Objects.requireNonNull(candidate, "JobCandidate must not be null");
        NormalizedJobCandidate normalized = jobNormalizer.normalize(candidate);
        jobIngestionValidator.validateOrThrow(normalized);
        return persistNormalized(normalized);
    }

    /**
     * Ingests a raw job ingestion candidate into the shared job catalog.
     * Normalizes the candidate, looks up any existing job by source identity, applies safe updates or creation, and persists.
     *
     * @param candidate the raw candidate to ingest
     * @return the normalized {@link JobResponse} projection
     */
    public JobResponse ingest(JobIngestionCandidate candidate) {
        return ingestCandidate(candidate).job();
    }

    /**
     * Ingests a standard job candidate into the shared job catalog.
     *
     * @param candidate the job candidate to ingest
     * @return the normalized {@link JobResponse} projection
     */
    public JobResponse ingest(JobCandidate candidate) {
        return ingestCandidate(candidate).job();
    }

    /**
     * Ingests a batch of raw job ingestion candidates sequentially.
     *
     * @param candidates list of raw ingestion candidates
     * @return list of resulting JobResponse projections
     */
    public List<JobResponse> ingestAllCandidates(List<JobIngestionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<JobResponse> responses = new ArrayList<>(candidates.size());
        for (JobIngestionCandidate candidate : candidates) {
            responses.add(ingest(candidate));
        }
        return responses;
    }

    /**
     * Ingests a batch of job candidates sequentially.
     *
     * @param candidates list of job candidates
     * @return list of resulting JobResponse projections
     */
    public List<JobResponse> ingestAll(List<JobCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<JobResponse> responses = new ArrayList<>(candidates.size());
        for (JobCandidate candidate : candidates) {
            responses.add(ingest(candidate));
        }
        return responses;
    }

    private JobCandidateIngestionResult persistNormalized(NormalizedJobCandidate candidate) {
        StoredJobDuplicateMatch duplicateMatch = jobDuplicateDetector.findStoredDuplicate(candidate);
        JobDuplicateResult duplicateResult = duplicateMatch.result();

        Job job;
        CandidateIngestionAction action;

        if (duplicateResult.classification() == JobDuplicateClassification.EXACT_SOURCE_DUPLICATE) {
            // A. EXACT_SOURCE_DUPLICATE: Idempotent update on existing source identity record
            job = duplicateMatch.matchedJob() != null
                    ? duplicateMatch.matchedJob()
                    : jobRepository.findBySourceAndExternalJobId(candidate.source(), candidate.externalJobId()).orElseThrow();
            updateJobAttributes(job, candidate);
            action = CandidateIngestionAction.UPDATED;
            log.info("Updated existing job in catalog [jobId={}, source={}, externalJobId={}]",
                    job.getId(), job.getSource(), job.getExternalJobId());
        } else if (duplicateResult.classification() == JobDuplicateClassification.EXACT_URL_DUPLICATE
                || duplicateResult.classification() == JobDuplicateClassification.EXACT_CONTENT_DUPLICATE) {
            // B & C. DETECTED_CROSS_SOURCE_DUPLICATE: Preserve both records, preserve provenance, do not merge or delete
            job = new Job(
                    candidate.source(),
                    candidate.externalJobId(),
                    candidate.title(),
                    candidate.companyName()
            );
            applyCandidateAttributes(job, candidate);
            action = CandidateIngestionAction.DETECTED_CROSS_SOURCE_DUPLICATE;
            log.info("Persisted cross-source duplicate job in catalog [classification={}, source={}, externalJobId={}]",
                    duplicateResult.classification(), candidate.source(), candidate.externalJobId());
        } else {
            // D. NOT_DUPLICATE: Standard new record persistence
            job = new Job(
                    candidate.source(),
                    candidate.externalJobId(),
                    candidate.title(),
                    candidate.companyName()
            );
            applyCandidateAttributes(job, candidate);
            action = CandidateIngestionAction.CREATED;
            log.info("Created new job in catalog [source={}, externalJobId={}]",
                    candidate.source(), candidate.externalJobId());
        }

        Job savedJob = jobRepository.save(job);
        return new JobCandidateIngestionResult(JobResponse.from(savedJob), action, duplicateResult, null);
    }

    /**
     * Updates mutable fields of an existing job with data loss protection.
     * Preserves existing non-null data when new candidate provides null or unknown values.
     */
    private void updateJobAttributes(Job job, NormalizedJobCandidate candidate) {
        // Mandatory fields
        job.setTitle(candidate.title());
        job.setCompanyName(candidate.companyName());

        // Optional text fields with data loss protection (preserve existing if new value is null)
        if (candidate.recruiterName() != null) {
            job.setRecruiterName(candidate.recruiterName());
        }
        if (candidate.description() != null) {
            job.setDescription(candidate.description());
        }
        if (candidate.location() != null) {
            job.setLocation(candidate.location());
        }

        // Enums: update only if new value is known; retain existing known values if new value is UNKNOWN
        if (candidate.workMode() != null && candidate.workMode() != JobWorkMode.UNKNOWN) {
            job.setWorkMode(candidate.workMode());
        }
        if (candidate.employmentType() != null && candidate.employmentType() != JobEmploymentType.UNKNOWN) {
            job.setEmploymentType(candidate.employmentType());
        }
        if (candidate.applicationMethod() != null && candidate.applicationMethod() != JobApplicationMethod.UNKNOWN) {
            job.setApplicationMethod(candidate.applicationMethod());
        }

        // Experience range with data loss protection and cross-validation
        Integer effectiveMinExp = candidate.experienceMinYears() != null ? candidate.experienceMinYears() : job.getExperienceMinYears();
        Integer effectiveMaxExp = candidate.experienceMaxYears() != null ? candidate.experienceMaxYears() : job.getExperienceMaxYears();
        if (effectiveMinExp != null && effectiveMaxExp != null && effectiveMinExp > effectiveMaxExp) {
            throw new JobValidationException(
                    "INVALID_EXPERIENCE_RANGE",
                    "experienceMinYears (" + effectiveMinExp + ") must not exceed experienceMaxYears (" + effectiveMaxExp + ")"
            );
        }
        if (candidate.experienceMinYears() != null) {
            job.setExperienceMinYears(candidate.experienceMinYears());
        }
        if (candidate.experienceMaxYears() != null) {
            job.setExperienceMaxYears(candidate.experienceMaxYears());
        }

        // Salary range with data loss protection and cross-validation
        BigDecimal effectiveMinSal = candidate.salaryMin() != null ? candidate.salaryMin() : job.getSalaryMin();
        BigDecimal effectiveMaxSal = candidate.salaryMax() != null ? candidate.salaryMax() : job.getSalaryMax();
        if (effectiveMinSal != null && effectiveMaxSal != null && effectiveMinSal.compareTo(effectiveMaxSal) > 0) {
            throw new JobValidationException(
                    "INVALID_SALARY_RANGE",
                    "salaryMin (" + effectiveMinSal + ") must not exceed salaryMax (" + effectiveMaxSal + ")"
            );
        }
        if (candidate.salaryMin() != null) {
            job.setSalaryMin(candidate.salaryMin());
        }
        if (candidate.salaryMax() != null) {
            job.setSalaryMax(candidate.salaryMax());
        }
        if (candidate.salaryCurrency() != null) {
            job.setSalaryCurrency(candidate.salaryCurrency());
        }
        if (candidate.salaryPeriod() != null) {
            job.setSalaryPeriod(candidate.salaryPeriod());
        }

        // URLs with data loss protection
        if (candidate.jobUrl() != null) {
            job.setJobUrl(candidate.jobUrl());
        }
        if (candidate.companyUrl() != null) {
            job.setCompanyUrl(candidate.companyUrl());
        }

        // Dates with cross-validation
        Instant effectivePosted = candidate.postedAt() != null ? candidate.postedAt() : job.getPostedAt();
        Instant effectiveExpires = candidate.expiresAt() != null ? candidate.expiresAt() : job.getExpiresAt();
        if (effectivePosted != null && effectiveExpires != null && effectiveExpires.isBefore(effectivePosted)) {
            throw new JobValidationException(
                    "INVALID_DATE_RANGE",
                    "expiresAt (" + effectiveExpires + ") cannot precede postedAt (" + effectivePosted + ")"
            );
        }
        if (candidate.postedAt() != null) {
            job.setPostedAt(candidate.postedAt());
        }
        if (candidate.expiresAt() != null) {
            job.setExpiresAt(candidate.expiresAt());
        }

        // Refresh lastSeenAt to latest seen timestamp without regression
        Instant candidateLastSeen = candidate.lastSeenAt() != null ? candidate.lastSeenAt() : Instant.now();
        if (job.getLastSeenAt() == null || candidateLastSeen.isAfter(job.getLastSeenAt())) {
            job.setLastSeenAt(candidateLastSeen);
        }
    }

    private void applyCandidateAttributes(Job job, NormalizedJobCandidate candidate) {
        job.setRecruiterName(candidate.recruiterName());
        job.setDescription(candidate.description());
        job.setLocation(candidate.location());
        job.setWorkMode(candidate.workMode());
        job.setEmploymentType(candidate.employmentType());
        job.setExperienceMinYears(candidate.experienceMinYears());
        job.setExperienceMaxYears(candidate.experienceMaxYears());
        job.setSalaryMin(candidate.salaryMin());
        job.setSalaryMax(candidate.salaryMax());
        job.setSalaryCurrency(candidate.salaryCurrency());
        job.setSalaryPeriod(candidate.salaryPeriod());
        job.setJobUrl(candidate.jobUrl());
        job.setCompanyUrl(candidate.companyUrl());
        job.setPostedAt(candidate.postedAt());
        job.setExpiresAt(candidate.expiresAt());
        job.setApplicationMethod(candidate.applicationMethod());

        if (candidate.discoveredAt() != null) {
            job.setDiscoveredAt(candidate.discoveredAt());
        }
        if (candidate.lastSeenAt() != null) {
            job.setLastSeenAt(candidate.lastSeenAt());
        }
    }
}
