package com.joblivo.job.model;

import java.util.Objects;

/**
 * Canonical descriptive source metadata definition for a supported {@link JobSource}.
 * <p>
 * Exposes strictly non-sensitive, stable descriptive information required by the application:
 * <ul>
 *     <li>Source enum origin</li>
 *     <li>Source code identifier</li>
 *     <li>Human-readable display name</li>
 *     <li>Current runtime enablement status</li>
 * </ul>
 * <p>
 * <strong>Security Boundary:</strong>
 * Strictly excludes credentials, OAuth tokens, passwords, authorization headers,
 * cookies, or scraper/crawler internals.
 */
public record JobSourceMetadata(
        JobSource source,
        String code,
        String displayName,
        boolean enabled
) {

    public JobSourceMetadata {
        Objects.requireNonNull(source, "JobSource must not be null");
        if (code == null || code.isBlank()) {
            code = source.code();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = source.displayName();
        }
    }

    /**
     * Constructs canonical metadata for a given {@link JobSource} and its enablement state.
     *
     * @param source  the job source origin
     * @param enabled whether the source is currently enabled
     * @return canonical JobSourceMetadata
     */
    public static JobSourceMetadata of(JobSource source, boolean enabled) {
        Objects.requireNonNull(source, "JobSource must not be null");
        return new JobSourceMetadata(source, source.code(), source.displayName(), enabled);
    }
}
