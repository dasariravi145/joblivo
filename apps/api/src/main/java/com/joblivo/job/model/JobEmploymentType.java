package com.joblivo.job.model;

/**
 * Normalized employment type classification for job postings.
 * Must not be inferred from job title alone; requires explicit or default source attribution.
 */
public enum JobEmploymentType {
    FULL_TIME,
    PART_TIME,
    CONTRACT,
    INTERNSHIP,
    TEMPORARY,
    FREELANCE,
    OTHER,
    UNKNOWN
}
