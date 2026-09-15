package com.joblivo.job.service;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Paginated response container for job discovery search results.
 */
public record JobSearchResponse(
        List<JobResponse> jobs,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static JobSearchResponse from(Page<JobResponse> page) {
        return new JobSearchResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
