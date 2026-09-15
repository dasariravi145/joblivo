package com.joblivo.job;

import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.service.JobResponse;
import com.joblivo.job.service.JobSearchCriteria;
import com.joblivo.job.service.JobSearchRequest;
import com.joblivo.job.service.JobSearchResponse;
import com.joblivo.job.service.JobSearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller providing deterministic job discovery and search capabilities over normalized jobs.
 * Requires an authenticated principal, while results remain source-neutral and shared across all platform users.
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobSearchService jobSearchService;

    public JobController(JobSearchService jobSearchService) {
        this.jobSearchService = Objects.requireNonNull(jobSearchService, "jobSearchService must not be null");
    }

    /**
     * Searches and filters normalized jobs using deterministic criteria, pagination, and sorting.
     *
     * @param keyword            free-text query matching title, company name, or description
     * @param location           location query matching job location
     * @param workMode           work mode filter
     * @param employmentType     employment type filter
     * @param source             job source filter
     * @param minExperienceYears minimum experience requirement filter
     * @param minimumExperience  alias for minimum experience filter
     * @param maxExperienceYears maximum experience requirement filter
     * @param maximumExperience  alias for maximum experience filter
     * @param minSalary          minimum salary requirement filter
     * @param minimumSalary      alias for minimum salary filter
     * @param maxSalary          maximum salary requirement filter
     * @param maximumSalary      alias for maximum salary filter
     * @param postedAfter        posted date lower bound (ISO-8601)
     * @param postedBefore       posted date upper bound (ISO-8601)
     * @param page               zero-based page index (default: 0)
     * @param size               page size (default: 20, max: 100)
     * @param sort               sort key: newest, oldest, company, title, relevance, freshness (orders ACTIVE/UNKNOWN/STALE/EXPIRED jobs deterministically; default: newest)
     * @param authentication     authenticated security principal
     * @return 200 OK with paginated JobSearchResponse
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JobSearchResponse> searchJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) JobWorkMode workMode,
            @RequestParam(required = false) JobEmploymentType employmentType,
            @RequestParam(required = false) JobSource source,
            @RequestParam(required = false) Integer minExperienceYears,
            @RequestParam(required = false) Integer minimumExperience,
            @RequestParam(required = false) Integer maxExperienceYears,
            @RequestParam(required = false) Integer maximumExperience,
            @RequestParam(required = false) BigDecimal minSalary,
            @RequestParam(required = false) BigDecimal minimumSalary,
            @RequestParam(required = false) BigDecimal maxSalary,
            @RequestParam(required = false) BigDecimal maximumSalary,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant postedAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant postedBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort,
            Authentication authentication
    ) {
        validateAuthentication(authentication);

        Integer effectiveMinExp = minExperienceYears != null ? minExperienceYears : minimumExperience;
        Integer effectiveMaxExp = maxExperienceYears != null ? maxExperienceYears : maximumExperience;
        BigDecimal effectiveMinSal = minSalary != null ? minSalary : minimumSalary;
        BigDecimal effectiveMaxSal = maxSalary != null ? maxSalary : maximumSalary;

        JobSearchRequest request = JobSearchRequest.builder()
                .keyword(keyword)
                .location(location)
                .workMode(workMode)
                .employmentType(employmentType)
                .source(source)
                .minExperienceYears(effectiveMinExp)
                .maxExperienceYears(effectiveMaxExp)
                .minSalary(effectiveMinSal)
                .maxSalary(effectiveMaxSal)
                .postedAfter(postedAfter)
                .postedBefore(postedBefore)
                .page(page)
                .size(size)
                .sort(sort)
                .build();

        JobSearchCriteria criteria = request.toCriteria();
        JobSearchResponse response = jobSearchService.search(criteria);
        return ResponseEntity.ok(response);
    }

    /**
     * Looks up a single normalized job record by ID.
     *
     * @param jobId          job UUID
     * @param authentication authenticated security principal
     * @return 200 OK with JobResponse
     */
    @GetMapping(value = "/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JobResponse> getJobById(
            @PathVariable("jobId") UUID jobId,
            Authentication authentication
    ) {
        validateAuthentication(authentication);

        JobResponse response = jobSearchService.findById(jobId);
        return ResponseEntity.ok(response);
    }

    private void validateAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Full authentication is required to access this resource");
        }
    }
}
