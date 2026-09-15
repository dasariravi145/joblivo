package com.joblivo.job.service;

import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Canonical, immutable request and query model for job discovery search.
 * Enforces production-safe validation, bounding, and normalization on incoming search parameters.
 */
public record JobSearchRequest(
        String keyword,
        String location,
        JobWorkMode workMode,
        JobEmploymentType employmentType,
        JobSource source,
        Integer minExperienceYears,
        Integer maxExperienceYears,
        BigDecimal minSalary,
        BigDecimal maxSalary,
        Instant postedAfter,
        Instant postedBefore,
        int page,
        int size,
        String sort
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final int MAX_TEXT_LENGTH = 200;
    public static final String DEFAULT_SORT = "newest";

    public JobSearchRequest {
        // 1. Text normalization & length bounds
        keyword = normalizeText(keyword, "Keyword");
        location = normalizeText(location, "Location");

        // 2. Pagination bounds
        if (page < 0) {
            throw new JobValidationException("Page index cannot be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new JobValidationException("Page size must be between 1 and " + MAX_SIZE + ", received: " + size);
        }

        // 3. Experience validation
        if (minExperienceYears != null && minExperienceYears < 0) {
            throw new JobValidationException("Minimum experience cannot be negative");
        }
        if (maxExperienceYears != null && maxExperienceYears < 0) {
            throw new JobValidationException("Maximum experience cannot be negative");
        }
        if (minExperienceYears != null && maxExperienceYears != null && minExperienceYears > maxExperienceYears) {
            throw new JobValidationException(
                    "Minimum experience (" + minExperienceYears + ") cannot exceed maximum experience (" + maxExperienceYears + ")"
            );
        }

        // 4. Salary validation
        if (minSalary != null && minSalary.compareTo(BigDecimal.ZERO) < 0) {
            throw new JobValidationException("Minimum salary cannot be negative");
        }
        if (maxSalary != null && maxSalary.compareTo(BigDecimal.ZERO) < 0) {
            throw new JobValidationException("Maximum salary cannot be negative");
        }
        if (minSalary != null && maxSalary != null && minSalary.compareTo(maxSalary) > 0) {
            throw new JobValidationException(
                    "Minimum salary (" + minSalary + ") cannot exceed maximum salary (" + maxSalary + ")"
            );
        }

        // 5. Date chronology validation
        if (postedAfter != null && postedBefore != null && postedAfter.isAfter(postedBefore)) {
            throw new JobValidationException(
                    "postedAfter (" + postedAfter + ") cannot be after postedBefore (" + postedBefore + ")"
            );
        }

        // 6. Sort allowlist validation
        if (sort == null || sort.isBlank()) {
            sort = DEFAULT_SORT;
        } else {
            sort = JobSearchSort.fromKey(sort).getKey();
        }
    }

    private static String normalizeText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new JobValidationException(fieldName + " length cannot exceed " + MAX_TEXT_LENGTH + " characters");
        }
        return trimmed;
    }

    /**
     * Backward-compatible alias for {@link #minExperienceYears()}.
     */
    public Integer minimumExperience() {
        return minExperienceYears;
    }

    /**
     * Backward-compatible alias for {@link #maxExperienceYears()}.
     */
    public Integer maximumExperience() {
        return maxExperienceYears;
    }

    /**
     * Backward-compatible alias for {@link #minSalary()}.
     */
    public BigDecimal minimumSalary() {
        return minSalary;
    }

    /**
     * Backward-compatible alias for {@link #maxSalary()}.
     */
    public BigDecimal maximumSalary() {
        return maxSalary;
    }

    /**
     * Converts this validated request to the internal {@link JobSearchCriteria}.
     *
     * @return canonical JobSearchCriteria
     */
    public JobSearchCriteria toCriteria() {
        return JobSearchCriteria.builder()
                .keyword(keyword)
                .location(location)
                .workMode(workMode)
                .employmentType(employmentType)
                .source(source)
                .minimumExperience(minExperienceYears)
                .maximumExperience(maxExperienceYears)
                .minimumSalary(minSalary)
                .maximumSalary(maxSalary)
                .postedAfter(postedAfter)
                .postedBefore(postedBefore)
                .page(page)
                .size(size)
                .sort(sort)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String keyword;
        private String location;
        private JobWorkMode workMode;
        private JobEmploymentType employmentType;
        private JobSource source;
        private Integer minExperienceYears;
        private Integer maxExperienceYears;
        private BigDecimal minSalary;
        private BigDecimal maxSalary;
        private Instant postedAfter;
        private Instant postedBefore;
        private Integer page;
        private Integer size;
        private String sort;

        public Builder keyword(String keyword) {
            this.keyword = keyword;
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

        public Builder source(JobSource source) {
            this.source = source;
            return this;
        }

        public Builder minExperienceYears(Integer minExperienceYears) {
            this.minExperienceYears = minExperienceYears;
            return this;
        }

        public Builder minimumExperience(Integer minimumExperience) {
            this.minExperienceYears = minimumExperience;
            return this;
        }

        public Builder maxExperienceYears(Integer maxExperienceYears) {
            this.maxExperienceYears = maxExperienceYears;
            return this;
        }

        public Builder maximumExperience(Integer maximumExperience) {
            this.maxExperienceYears = maximumExperience;
            return this;
        }

        public Builder minSalary(BigDecimal minSalary) {
            this.minSalary = minSalary;
            return this;
        }

        public Builder minimumSalary(BigDecimal minimumSalary) {
            this.minSalary = minimumSalary;
            return this;
        }

        public Builder maxSalary(BigDecimal maxSalary) {
            this.maxSalary = maxSalary;
            return this;
        }

        public Builder maximumSalary(BigDecimal maximumSalary) {
            this.maxSalary = maximumSalary;
            return this;
        }

        public Builder postedAfter(Instant postedAfter) {
            this.postedAfter = postedAfter;
            return this;
        }

        public Builder postedBefore(Instant postedBefore) {
            this.postedBefore = postedBefore;
            return this;
        }

        public Builder page(Integer page) {
            this.page = page;
            return this;
        }

        public Builder size(Integer size) {
            this.size = size;
            return this;
        }

        public Builder sort(String sort) {
            this.sort = sort;
            return this;
        }

        public JobSearchRequest build() {
            int effectivePage = page != null ? page : DEFAULT_PAGE;
            int effectiveSize = size != null ? size : DEFAULT_SIZE;
            String effectiveSort = (sort != null && !sort.isBlank()) ? sort : DEFAULT_SORT;

            return new JobSearchRequest(
                    keyword,
                    location,
                    workMode,
                    employmentType,
                    source,
                    minExperienceYears,
                    maxExperienceYears,
                    minSalary,
                    maxSalary,
                    postedAfter,
                    postedBefore,
                    effectivePage,
                    effectiveSize,
                    effectiveSort
            );
        }
    }
}
