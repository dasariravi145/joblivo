package com.joblivo.job.ingestion;

/**
 * Immutable criteria parameters for source adapters when discovering or querying jobs.
 * <p>
 * Contains only source-neutral retrieval parameters (query, location, limit).
 * Strictly contains no credentials, OAuth tokens, browser sessions, or user identities.
 *
 * @param query    optional search keyword or title
 * @param location optional geographical filter
 * @param limit    maximum number of candidate jobs to retrieve (default 50, upper bound 1000)
 */
public record JobFetchCriteria(
        String query,
        String location,
        int limit
) {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 1000;

    public JobFetchCriteria {
        query = query != null && !query.isBlank() ? query.trim() : null;
        location = location != null && !location.isBlank() ? location.trim() : null;
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        } else if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
    }

    public static JobFetchCriteria of(String query, String location) {
        return new JobFetchCriteria(query, location, DEFAULT_LIMIT);
    }

    public static JobFetchCriteria of(String query, String location, int limit) {
        return new JobFetchCriteria(query, location, limit);
    }
}
