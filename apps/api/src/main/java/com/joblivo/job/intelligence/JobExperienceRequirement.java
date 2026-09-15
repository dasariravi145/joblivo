package com.joblivo.job.intelligence;

/**
 * Structured representation of an explicit professional experience requirement.
 *
 * @param minimumYears minimum required years of experience (or null if not bounded)
 * @param maximumYears maximum required years of experience (or null if not bounded)
 * @param rawEvidence  verbatim excerpt from the job description supporting this requirement
 */
public record JobExperienceRequirement(
        Integer minimumYears,
        Integer maximumYears,
        String rawEvidence
) {
    public JobExperienceRequirement {
        rawEvidence = rawEvidence != null ? rawEvidence.trim() : "";
    }

    public static JobExperienceRequirement of(Integer min, Integer max, String rawEvidence) {
        return new JobExperienceRequirement(min, max, rawEvidence);
    }

    public static JobExperienceRequirement min(int min, String rawEvidence) {
        return new JobExperienceRequirement(min, null, rawEvidence);
    }

    public static JobExperienceRequirement range(int min, int max, String rawEvidence) {
        return new JobExperienceRequirement(min, max, rawEvidence);
    }
}
