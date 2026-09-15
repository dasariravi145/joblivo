package com.joblivo.job.ingestion;

/**
 * Canonical high-level source coverage status within the Job Discovery subsystem.
 * <p>
 * Categorizes each job source identity into one of four deterministic states:
 * <ol>
 *   <li>{@link #SUPPORTED}: An adapter is implemented AND enabled in configuration. Execution permitted.</li>
 *   <li>{@link #CONFIGURED_BUT_DISABLED}: An adapter is implemented, but disabled in configuration. Execution not permitted.</li>
 *   <li>{@link #NOT_IMPLEMENTED}: No adapter is implemented for this source. Execution is impossible regardless of configuration.</li>
 *   <li>{@link #UNKNOWN}: Source identity is null or unrecognized. Execution not permitted.</li>
 * </ol>
 */
public enum JobSourceCoverageStatus {

    /**
     * Source is implemented with a registered adapter AND configured as enabled.
     * Ingestion execution is currently permitted.
     */
    SUPPORTED,

    /**
     * Source is implemented with a registered adapter, but currently disabled in configuration
     * (or master discovery toggle is disabled). Ingestion execution is not permitted.
     */
    CONFIGURED_BUT_DISABLED,

    /**
     * Source is known conceptually in the domain, but has no registered adapter implementation.
     * Ingestion execution is strictly prohibited. An unimplemented source can NEVER execute,
     * even if configuration requests enabled=true.
     */
    NOT_IMPLEMENTED,

    /**
     * Source is null or not a recognized domain source identity.
     * Ingestion execution is strictly prohibited.
     */
    UNKNOWN;

    /**
     * Returns true if execution is permitted under this coverage status (i.e. {@link #SUPPORTED}).
     */
    public boolean isExecutionPermitted() {
        return this == SUPPORTED;
    }
}
