package com.joblivo.truth.model;

/**
 * Factual category of a career claim, justified strictly by the Joblivo Master Career Profile domain.
 */
public enum ClaimCategory {

    /**
     * Skill, technology, programming language, framework, or competency.
     */
    SKILL,

    /**
     * Employment history or complete work experience entry (company + role + timeline).
     */
    EXPERIENCE,

    /**
     * Professional job title or designation.
     */
    JOB_TITLE,

    /**
     * Employer, client organization, or company name.
     */
    COMPANY,

    /**
     * Portfolio project, initiative, or product engagement.
     */
    PROJECT,

    /**
     * Professional achievement, award, honor, or key milestone.
     */
    ACHIEVEMENT,

    /**
     * Industry license, credential, or formal certification.
     */
    CERTIFICATION,

    /**
     * Degree, academic institution, major, or educational credential.
     */
    EDUCATION,

    /**
     * Day-to-day job responsibility, task, or team role.
     */
    RESPONSIBILITY,

    /**
     * Quantifiable scale metric, revenue figure, team count, performance percentage, or headcount.
     */
    METRIC,

    /**
     * Geographic work location, country, city, or remote work preference.
     */
    LOCATION,

    /**
     * Other professional career statement not captured by above categories.
     */
    OTHER
}
