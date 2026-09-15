package com.joblivo.job.model;

/**
 * Deterministic aggregate outcome for a job's authenticity assessment.
 * <p>
 * Communicates the overall signal posture without making unfounded claims such as
 * "LEGITIMATE" or "SCAM".
 */
public enum JobAuthenticityOutcome {
    /**
     * No warning or informational signals were detected from available job metadata.
     */
    NO_WARNING,

    /**
     * Informational signals detected (e.g. stale job, expired job, or missing non-critical details)
     * with no high or medium risk inconsistencies.
     */
    INFORMATIONAL,

    /**
     * Moderate warning signals detected (e.g. invalid application method, missing URL)
     * warranting job seeker caution.
     */
    CAUTION,

    /**
     * High-risk objective inconsistencies detected (e.g. inverted salary range, inverted experience,
     * missing provenance).
     */
    HIGH_RISK_SIGNALS,

    /**
     * Insufficient evidence available to evaluate authenticity (e.g. core required fields missing).
     */
    INSUFFICIENT_EVIDENCE
}
