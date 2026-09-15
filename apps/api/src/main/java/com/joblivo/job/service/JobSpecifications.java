package com.joblivo.job.service;

import com.joblivo.job.Job;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications builder for deterministic job discovery search queries.
 */
public final class JobSpecifications {

    private JobSpecifications() {
    }

    public static Specification<Job> withCriteria(JobSearchCriteria criteria) {
        return withCriteria(criteria, new JobFreshnessEvaluator());
    }

    public static Specification<Job> withCriteria(JobSearchCriteria criteria, JobFreshnessEvaluator freshnessEvaluator) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Keyword search (case-insensitive substring in title, companyName, location, description)
            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String normalizedKw = JobSearchRelevanceOrder.normalizeKeyword(criteria.keyword());
                String pattern = "%" + normalizedKw + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("companyName")), pattern),
                        cb.like(cb.lower(root.get("location")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                ));
            }

            // 2. Location search (case-insensitive substring)
            if (criteria.location() != null && !criteria.location().isBlank()) {
                String pattern = "%" + criteria.location().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("location")), pattern));
            }

            // 3. Work mode exact match
            if (criteria.workMode() != null) {
                predicates.add(cb.equal(root.get("workMode"), criteria.workMode()));
            }

            // 4. Employment type exact match
            if (criteria.employmentType() != null) {
                predicates.add(cb.equal(root.get("employmentType"), criteria.employmentType()));
            }

            // 5. Source exact match
            if (criteria.source() != null) {
                predicates.add(cb.equal(root.get("source"), criteria.source()));
            }

            // 6. Experience range overlap
            if (criteria.minimumExperience() != null || criteria.maximumExperience() != null) {
                // Exclude jobs with completely unstated experience
                predicates.add(cb.or(
                        cb.isNotNull(root.get("experienceMinYears")),
                        cb.isNotNull(root.get("experienceMaxYears"))
                ));

                if (criteria.minimumExperience() != null) {
                    predicates.add(cb.or(
                            cb.greaterThanOrEqualTo(root.get("experienceMaxYears"), criteria.minimumExperience()),
                            cb.and(
                                    cb.isNull(root.get("experienceMaxYears")),
                                    cb.isNotNull(root.get("experienceMinYears"))
                            )
                    ));
                }

                if (criteria.maximumExperience() != null) {
                    predicates.add(cb.or(
                            cb.lessThanOrEqualTo(root.get("experienceMinYears"), criteria.maximumExperience()),
                            cb.and(
                                    cb.isNull(root.get("experienceMinYears")),
                                    cb.isNotNull(root.get("experienceMaxYears"))
                            )
                    ));
                }
            }

            // 7. Salary range overlap
            if (criteria.minimumSalary() != null || criteria.maximumSalary() != null) {
                // Exclude jobs with completely unstated salary
                predicates.add(cb.or(
                        cb.isNotNull(root.get("salaryMin")),
                        cb.isNotNull(root.get("salaryMax"))
                ));

                if (criteria.minimumSalary() != null) {
                    predicates.add(cb.or(
                            cb.greaterThanOrEqualTo(root.get("salaryMax"), criteria.minimumSalary()),
                            cb.and(
                                    cb.isNull(root.get("salaryMax")),
                                    cb.greaterThanOrEqualTo(root.get("salaryMin"), criteria.minimumSalary())
                            )
                    ));
                }

                if (criteria.maximumSalary() != null) {
                    predicates.add(cb.or(
                            cb.lessThanOrEqualTo(root.get("salaryMin"), criteria.maximumSalary()),
                            cb.and(
                                    cb.isNull(root.get("salaryMin")),
                                    cb.lessThanOrEqualTo(root.get("salaryMax"), criteria.maximumSalary())
                            )
                    ));
                }
            }

            // 8. Posted date range
            if (criteria.postedAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("postedAt"), criteria.postedAfter()));
            }
            if (criteria.postedBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("postedAt"), criteria.postedBefore()));
            }

            // 9. Deterministic ordering applied to data queries before pagination
            if (query != null && (query.getResultType() == null || !Number.class.isAssignableFrom(query.getResultType()))) {
                JobSearchSort sort = JobSearchSort.fromKey(criteria.sort());
                if (sort == JobSearchSort.RELEVANCE && criteria.keyword() != null && !criteria.keyword().isBlank()) {
                    JobSearchRelevanceOrder.applyRelevanceOrder(cb, query, root, criteria.keyword());
                } else if (sort == JobSearchSort.FRESHNESS) {
                    JobSearchFreshnessOrder.applyFreshnessOrder(cb, query, root, freshnessEvaluator);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
