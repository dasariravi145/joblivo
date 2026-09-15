package com.joblivo.truth.model;

/**
 * Origin source type of factual evidence within the Master Career Profile.
 */
public enum EvidenceSourceType {

    /**
     * Core career profile identity (headline, current title, current company, total experience, location).
     */
    CAREER_PROFILE,

    /**
     * Specific work experience entry.
     */
    WORK_EXPERIENCE,

    /**
     * Specific skill / technology entry.
     */
    SKILL,

    /**
     * Specific project entry.
     */
    PROJECT,

    /**
     * Specific education qualification entry.
     */
    EDUCATION,

    /**
     * Specific certification entry.
     */
    CERTIFICATION,

    /**
     * Specific achievement entry.
     */
    ACHIEVEMENT
}
