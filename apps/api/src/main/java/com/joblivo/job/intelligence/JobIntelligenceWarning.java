package com.joblivo.job.intelligence;

/**
 * Controlled warning indicators identifying limitations encountered during deterministic job description parsing.
 */
public enum JobIntelligenceWarning {
    /**
     * The job record description was null, empty, or whitespace-only.
     */
    DESCRIPTION_EMPTY,

    /**
     * No recognizable section headings were found; the entire description was preserved unclassified.
     */
    NO_RELIABLE_SECTIONS_DETECTED,

    /**
     * Qualifications or skills could not be unambiguously categorized into required versus preferred.
     */
    AMBIGUOUS_REQUIREMENT_CLASSIFICATION,

    /**
     * No explicit numerical years of experience statement was detected in the text.
     */
    EXPERIENCE_NOT_EXPLICIT,

    /**
     * Skill extraction was limited due to absence of recognizable technology keywords.
     */
    SKILL_EXTRACTION_LIMITED
}
