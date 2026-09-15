package com.joblivo.job.model;

/**
 * Controlled, source-neutral enumeration of potential job discovery origins.
 * Identifiers represent source origins; integrations must remain decoupled and permitted.
 */
public enum JobSource {
    LINKEDIN("LinkedIn"),
    NAUKRI("Naukri"),
    FOUNDIT("Foundit"),
    CUTSHORT("Cutshort"),
    INSTAHYRE("Instahyre"),
    COMPANY_CAREERS("Company Careers"),
    ATS("Applicant Tracking System (ATS)"),
    OTHER("Other");

    private final String displayName;

    JobSource(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Stable, non-sensitive identifier code for this job source (matches enum name).
     *
     * @return non-null uppercase code string
     */
    public String code() {
        return name();
    }

    /**
     * Human-readable display name for this job source.
     *
     * @return non-null display name string
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Resolves a {@link JobSource} from a case-insensitive code string.
     *
     * @param rawCode the source code to resolve
     * @return Optional containing the matching JobSource, or empty if unknown or blank
     */
    public static java.util.Optional<JobSource> fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = rawCode.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return java.util.Optional.of(JobSource.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
