package com.joblivo.job.service;

import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Filter criteria for deterministic job discovery search queries.
 */
public record JobSearchCriteria(
        String keyword,
        String location,
        JobWorkMode workMode,
        JobEmploymentType employmentType,
        JobSource source,
        Integer minimumExperience,
        Integer maximumExperience,
        BigDecimal minimumSalary,
        BigDecimal maximumSalary,
        Instant postedAfter,
        Instant postedBefore,
        int page,
        int size,
        String sort
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT = "newest";

    public JobSearchCriteria {
        keyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        location = (location != null && !location.isBlank()) ? location.trim() : null;
        if (sort == null || sort.isBlank()) {
            sort = DEFAULT_SORT;
        } else {
            sort = sort.trim();
        }
    }

    public Integer minExperienceYears() {
        return minimumExperience;
    }

    public Integer maxExperienceYears() {
        return maximumExperience;
    }

    public BigDecimal minSalary() {
        return minimumSalary;
    }

    public BigDecimal maxSalary() {
        return maximumSalary;
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
        private Integer minimumExperience;
        private Integer maximumExperience;
        private BigDecimal minimumSalary;
        private BigDecimal maximumSalary;
        private Instant postedAfter;
        private Instant postedBefore;
        private int page = DEFAULT_PAGE;
        private int size = DEFAULT_SIZE;
        private String sort = DEFAULT_SORT;

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

        public Builder minimumExperience(Integer minimumExperience) {
            this.minimumExperience = minimumExperience;
            return this;
        }

        public Builder minExperienceYears(Integer minExperienceYears) {
            this.minimumExperience = minExperienceYears;
            return this;
        }

        public Builder maximumExperience(Integer maximumExperience) {
            this.maximumExperience = maximumExperience;
            return this;
        }

        public Builder maxExperienceYears(Integer maxExperienceYears) {
            this.maximumExperience = maxExperienceYears;
            return this;
        }

        public Builder minimumSalary(BigDecimal minimumSalary) {
            this.minimumSalary = minimumSalary;
            return this;
        }

        public Builder minSalary(BigDecimal minSalary) {
            this.minimumSalary = minSalary;
            return this;
        }

        public Builder maximumSalary(BigDecimal maximumSalary) {
            this.maximumSalary = maximumSalary;
            return this;
        }

        public Builder maxSalary(BigDecimal maxSalary) {
            this.maximumSalary = maxSalary;
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

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder sort(String sort) {
            this.sort = sort;
            return this;
        }

        public JobSearchCriteria build() {
            return new JobSearchCriteria(
                    keyword, location, workMode, employmentType, source,
                    minimumExperience, maximumExperience,
                    minimumSalary, maximumSalary,
                    postedAfter, postedBefore,
                    page, size, sort
            );
        }
    }
}
