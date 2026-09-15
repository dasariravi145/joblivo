package com.joblivo.job.normalizer;

import com.joblivo.job.ingestion.JobCandidate;
import com.joblivo.job.ingestion.JobIngestionCandidate;

/**
 * Dedicated internal contract for deterministic, source-neutral job normalization and validation.
 * Operates as a pure in-memory domain service with zero persistence side effects or network calls.
 */
public interface JobNormalizer {

    /**
     * Normalizes and validates a raw ingestion candidate received from a source adapter.
     *
     * @param candidate raw ingestion candidate
     * @return clean, normalized job data ready for persistence
     */
    NormalizedJobCandidate normalize(JobIngestionCandidate candidate);

    /**
     * Normalizes and validates a standard JobCandidate.
     *
     * @param candidate job candidate
     * @return clean, normalized job data ready for persistence
     */
    NormalizedJobCandidate normalize(JobCandidate candidate);

    /**
     * Canonical deterministic normalization for company names.
     * Trims whitespace, collapses internal whitespace, applies Unicode NFC normalization,
     * and preserves casing, punctuation, and meaningful legal suffixes without fabricating info.
     *
     * @param companyName raw company name
     * @return canonical normalized company name
     * @throws com.joblivo.job.exception.JobValidationException if companyName is null or blank
     */
    String normalizeCompanyName(String companyName);

    /**
     * Canonical deterministic normalization for job titles.
     * Trims whitespace, collapses internal whitespace, applies Unicode NFC normalization,
     * and preserves seniority, technology terms, abbreviations, and punctuation without
     * classifying, generalizing, or performing synonym expansion.
     *
     * @param title raw job title
     * @return canonical normalized job title
     * @throws com.joblivo.job.exception.JobValidationException if title is null or blank
     */
    String normalizeTitle(String title);

    /**
     * Canonical deterministic normalization for locations.
     * Trims whitespace, collapses internal whitespace, applies Unicode NFC normalization,
     * and preserves commas, hyphens, and remote/hybrid/onsite wording without geocoding or inference.
     * Returns null if location is null or blank (optional field).
     *
     * @param location raw location string
     * @return canonical normalized location, or null if null/blank
     */
    String normalizeLocation(String location);
}
