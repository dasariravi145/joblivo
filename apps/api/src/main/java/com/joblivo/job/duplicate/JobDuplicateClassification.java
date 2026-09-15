package com.joblivo.job.duplicate;

/**
 * Strict enumeration of deterministic duplicate evidence classifications for Job records.
 * <p>
 * Signals are evaluated according to a fixed precedence:
 * <ol>
 *   <li>{@link #EXACT_SOURCE_DUPLICATE}: Identical source origin and external job identifier.</li>
 *   <li>{@link #EXACT_URL_DUPLICATE}: Identical canonical, normalized HTTP/HTTPS job posting URL.</li>
 *   <li>{@link #EXACT_CONTENT_DUPLICATE}: Identical canonical SHA-256 content fingerprint.</li>
 *   <li>{@link #NOT_DUPLICATE}: No deterministic duplicate evidence found.</li>
 * </ol>
 */
public enum JobDuplicateClassification {
    EXACT_SOURCE_DUPLICATE,
    EXACT_URL_DUPLICATE,
    EXACT_CONTENT_DUPLICATE,
    NOT_DUPLICATE;

    /**
     * Checks if this classification represents any deterministic duplicate match.
     *
     * @return {@code true} if a duplicate signal was matched, {@code false} if {@link #NOT_DUPLICATE}
     */
    public boolean isDuplicate() {
        return this != NOT_DUPLICATE;
    }
}
