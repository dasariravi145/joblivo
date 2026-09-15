package com.joblivo.job.ingestion;

/**
 * Deterministic internal status indicating whether a real {@link JobSourceAdapter}
 * implementation exists in the application runtime.
 * <p>
 * A source is {@link #IMPLEMENTED} only when an authoritative {@link JobSourceAdapter}
 * is registered in {@link JobSourceRegistry}.
 * It is never marked implemented merely because an enum, configuration, or documentation exists.
 */
public enum JobSourceImplementationStatus {

    /**
     * An authoritative, registered {@link JobSourceAdapter} exists for this source.
     */
    IMPLEMENTED,

    /**
     * No {@link JobSourceAdapter} exists for this source.
     * An unimplemented source can never become executable, regardless of configuration.
     */
    NOT_IMPLEMENTED;

    /**
     * Returns true if this status represents an implemented source adapter.
     */
    public boolean isImplemented() {
        return this == IMPLEMENTED;
    }
}
