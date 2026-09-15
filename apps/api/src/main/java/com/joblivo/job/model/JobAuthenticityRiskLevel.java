package com.joblivo.job.model;

/**
 * Deterministic risk level representing the severity of an authenticity warning signal.
 * <p>
 * <strong>Important:</strong> These levels represent the strength of a warning signal,
 * <em>not</em> the statistical probability of fraud.
 */
public enum JobAuthenticityRiskLevel {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int severity;

    JobAuthenticityRiskLevel(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }

    /**
     * Returns true if this risk level has a severity greater than or equal to the specified other level.
     *
     * @param other the risk level to compare against
     * @return true if this severity is &gt;= other severity
     */
    public boolean isAtLeast(JobAuthenticityRiskLevel other) {
        if (other == null) {
            return false;
        }
        return this.severity >= other.severity;
    }

    /**
     * Resolves the highest risk level between this and another level.
     *
     * @param other the other risk level
     * @return the risk level with the higher severity
     */
    public JobAuthenticityRiskLevel max(JobAuthenticityRiskLevel other) {
        if (other == null) {
            return this;
        }
        return this.severity >= other.severity ? this : other;
    }
}
