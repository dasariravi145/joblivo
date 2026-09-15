package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.JobRepository;
import com.joblivo.job.exception.JobNotFoundException;
import com.joblivo.job.exception.JobValidationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Service providing deterministic query, search, and pagination over normalized Job records.
 * Contains zero AI dependencies and zero external source integrations.
 */
@Service
@Transactional(readOnly = true)
public class JobSearchService {

    private final JobRepository jobRepository;
    private final JobFreshnessPolicy freshnessPolicy;
    private final JobResponseMapper jobResponseMapper;

    public JobSearchService(JobRepository jobRepository, JobResponseMapper jobResponseMapper, JobFreshnessPolicy freshnessPolicy) {
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository must not be null");
        this.freshnessPolicy = Objects.requireNonNullElseGet(freshnessPolicy, JobFreshnessPolicy::new);
        this.jobResponseMapper = Objects.requireNonNullElseGet(
                jobResponseMapper,
                () -> new JobResponseMapper(this.freshnessPolicy.getEvaluator())
        );
    }

    public JobSearchService(JobRepository jobRepository, JobFreshnessPolicy freshnessPolicy) {
        this(
                jobRepository,
                new JobResponseMapper(freshnessPolicy != null ? freshnessPolicy.getEvaluator() : new JobFreshnessEvaluator()),
                freshnessPolicy
        );
    }

    public JobSearchService(JobRepository jobRepository, JobFreshnessEvaluator freshnessEvaluator) {
        this(jobRepository, new JobFreshnessPolicy(freshnessEvaluator));
    }

    public JobSearchService(JobRepository jobRepository) {
        this(jobRepository, new JobFreshnessPolicy());
    }

    /**
     * Executes a deterministic search over normalized jobs matching the provided criteria.
     *
     * @param criteria query filters, pagination, and sort options
     * @return paginated search response with JobResponse DTOs
     * @throws JobValidationException if any filter parameter fails validation
     */

    /**
     * Executes a deterministic search over normalized jobs matching the provided criteria.
     *
     * @param criteria query filters, pagination, and sort options
     * @return paginated search response with JobResponse DTOs
     * @throws JobValidationException if any filter parameter fails validation
     */
    public JobSearchResponse search(JobSearchCriteria criteria) {
        if (criteria == null) {
            throw new JobValidationException("Search criteria cannot be null");
        }

        validateCriteria(criteria);

        JobSearchSort sort = JobSearchSort.fromKey(criteria.sort());
        if (sort == JobSearchSort.RELEVANCE && (criteria.keyword() == null || criteria.keyword().isBlank())) {
            sort = JobSearchSort.NEWEST;
        }

        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), sort.toSort());
        Specification<Job> spec = JobSpecifications.withCriteria(criteria, freshnessPolicy.getEvaluator());

        Page<Job> jobPage = jobRepository.findAll(spec, pageable);
        Page<JobResponse> responsePage = jobPage.map(this::toJobResponse);

        return JobSearchResponse.from(responsePage);
    }

    /**
     * Looks up a single job record by ID.
     *
     * @param id UUID of the job
     * @return JobResponse projection
     * @throws JobValidationException if id is null
     * @throws JobNotFoundException   if no job exists with the given ID
     */
    public JobResponse findById(UUID id) {
        if (id == null) {
            throw new JobValidationException("Job ID cannot be null");
        }
        return jobRepository.findById(id)
                .map(this::toJobResponse)
                .orElseThrow(() -> new JobNotFoundException(id));
    }

    private JobResponse toJobResponse(Job job) {
        return jobResponseMapper.toResponse(job, freshnessPolicy.evaluate(job));
    }

    public JobResponseMapper getJobResponseMapper() {
        return jobResponseMapper;
    }

    public JobFreshnessPolicy getFreshnessPolicy() {
        return freshnessPolicy;
    }

    public JobFreshnessEvaluator getFreshnessEvaluator() {
        return freshnessPolicy.getEvaluator();
    }

    private void validateCriteria(JobSearchCriteria criteria) {
        if (criteria.page() < 0) {
            throw new JobValidationException("Page index cannot be negative");
        }
        if (criteria.size() < 1 || criteria.size() > JobSearchCriteria.MAX_SIZE) {
            throw new JobValidationException(
                    "Page size must be between 1 and " + JobSearchCriteria.MAX_SIZE + ", received: " + criteria.size()
            );
        }

        // Experience validations
        if (criteria.minimumExperience() != null && criteria.minimumExperience() < 0) {
            throw new JobValidationException("Minimum experience cannot be negative");
        }
        if (criteria.maximumExperience() != null && criteria.maximumExperience() < 0) {
            throw new JobValidationException("Maximum experience cannot be negative");
        }
        if (criteria.minimumExperience() != null && criteria.maximumExperience() != null
                && criteria.minimumExperience() > criteria.maximumExperience()) {
            throw new JobValidationException(
                    "Minimum experience (" + criteria.minimumExperience() + ") cannot exceed maximum experience ("
                            + criteria.maximumExperience() + ")"
            );
        }

        // Salary validations
        if (criteria.minimumSalary() != null && criteria.minimumSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw new JobValidationException("Minimum salary cannot be negative");
        }
        if (criteria.maximumSalary() != null && criteria.maximumSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw new JobValidationException("Maximum salary cannot be negative");
        }
        if (criteria.minimumSalary() != null && criteria.maximumSalary() != null
                && criteria.minimumSalary().compareTo(criteria.maximumSalary()) > 0) {
            throw new JobValidationException(
                    "Minimum salary (" + criteria.minimumSalary() + ") cannot exceed maximum salary ("
                            + criteria.maximumSalary() + ")"
            );
        }

        // Date validations
        if (criteria.postedAfter() != null && criteria.postedBefore() != null
                && criteria.postedAfter().isAfter(criteria.postedBefore())) {
            throw new JobValidationException(
                    "postedAfter (" + criteria.postedAfter() + ") cannot be after postedBefore ("
                            + criteria.postedBefore() + ")"
            );
        }

        // Keyword and Location length bounds
        if (criteria.keyword() != null && criteria.keyword().length() > JobSearchRequest.MAX_TEXT_LENGTH) {
            throw new JobValidationException("Keyword length cannot exceed " + JobSearchRequest.MAX_TEXT_LENGTH + " characters");
        }
        if (criteria.location() != null && criteria.location().length() > JobSearchRequest.MAX_TEXT_LENGTH) {
            throw new JobValidationException("Location length cannot exceed " + JobSearchRequest.MAX_TEXT_LENGTH + " characters");
        }

        // Sort allowlist validation
        JobSearchSort.fromKey(criteria.sort());
    }
}
