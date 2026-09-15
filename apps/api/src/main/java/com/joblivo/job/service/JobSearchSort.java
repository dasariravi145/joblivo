package com.joblivo.job.service;

import com.joblivo.job.exception.JobValidationException;
import org.springframework.data.domain.Sort;

/**
 * Strict allow-list of deterministic sort orders for job discovery search queries.
 * Always includes secondary sorting on ID to guarantee deterministic ordering.
 */
public enum JobSearchSort {
    NEWEST("newest", Sort.by(Sort.Order.desc("postedAt").nullsLast(), Sort.Order.desc("id"))),
    OLDEST("oldest", Sort.by(Sort.Order.asc("postedAt").nullsLast(), Sort.Order.asc("id"))),
    COMPANY("company", Sort.by(Sort.Order.asc("companyName"), Sort.Order.asc("id"))),
    TITLE("title", Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"))),
    RELEVANCE("relevance", Sort.unsorted()),
    FRESHNESS("freshness", Sort.unsorted());

    private final String key;
    private final Sort sort;

    JobSearchSort(String key, Sort sort) {
        this.key = key;
        this.sort = sort;
    }

    public String getKey() {
        return key;
    }

    public Sort toSort() {
        return sort;
    }

    public static JobSearchSort fromKey(String key) {
        if (key == null || key.isBlank()) {
            return NEWEST;
        }
        String normalized = key.trim().toLowerCase();
        for (JobSearchSort sortOption : values()) {
            if (sortOption.key.equals(normalized)) {
                return sortOption;
            }
        }
        throw new JobValidationException(
                "Invalid sort parameter '" + key + "'. Supported values: newest, oldest, company, title, relevance, freshness"
        );
    }
}
