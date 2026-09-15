package com.joblivo.job.intelligence;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable canonical read model representing structured intelligence parsed from a job description.
 * <p>
 * Transforms raw job description text into source-neutral structured signals without modifying
 * the original text, without fabricating unmentioned requirements, and without calling external AI providers.
 *
 * @param jobId                      UUID of the associated job record (or null if parsing standalone text)
 * @param originalDescriptionPresent true if a non-blank original description was available to parse
 * @param summary                    extracted summary or overview text
 * @param responsibilities           list of explicit duties, expectations, or responsibilities
 * @param requiredQualifications     list of explicit mandatory qualifications or requirements
 * @param preferredQualifications    list of explicit desirable, nice-to-have, or bonus qualifications
 * @param requiredSkills             list of explicit technologies/skills identified in a mandatory context
 * @param preferredSkills            list of explicit technologies/skills identified in a preferred context
 * @param experienceRequirements     structured numerical experience bounds and supporting text excerpt (or null)
 * @param educationRequirements      list of explicit educational degrees or qualifications detected
 * @param certificationRequirements  list of explicit professional certifications detected
 * @param locationRequirements       list of explicit geographical or office locations detected
 * @param workModeRequirements       list of explicit work arrangement keywords (e.g. Remote, Hybrid, Onsite)
 * @param keywords                   bounded set of normalized keywords for downstream search/indexing
 * @param warnings                   list of parsing limitation warnings
 */
public record JobDescriptionIntelligence(
        UUID jobId,
        boolean originalDescriptionPresent,
        String summary,
        List<String> responsibilities,
        List<String> requiredQualifications,
        List<String> preferredQualifications,
        List<String> requiredSkills,
        List<String> preferredSkills,
        JobExperienceRequirement experienceRequirements,
        List<String> educationRequirements,
        List<String> certificationRequirements,
        List<String> locationRequirements,
        List<String> workModeRequirements,
        List<String> keywords,
        List<JobIntelligenceWarning> warnings
) {
    public JobDescriptionIntelligence {
        summary = summary != null ? summary.trim() : "";
        responsibilities = responsibilities != null ? List.copyOf(responsibilities) : Collections.emptyList();
        requiredQualifications = requiredQualifications != null ? List.copyOf(requiredQualifications) : Collections.emptyList();
        preferredQualifications = preferredQualifications != null ? List.copyOf(preferredQualifications) : Collections.emptyList();
        requiredSkills = requiredSkills != null ? List.copyOf(requiredSkills) : Collections.emptyList();
        preferredSkills = preferredSkills != null ? List.copyOf(preferredSkills) : Collections.emptyList();
        educationRequirements = educationRequirements != null ? List.copyOf(educationRequirements) : Collections.emptyList();
        certificationRequirements = certificationRequirements != null ? List.copyOf(certificationRequirements) : Collections.emptyList();
        locationRequirements = locationRequirements != null ? List.copyOf(locationRequirements) : Collections.emptyList();
        workModeRequirements = workModeRequirements != null ? List.copyOf(workModeRequirements) : Collections.emptyList();
        keywords = keywords != null ? List.copyOf(keywords) : Collections.emptyList();
        warnings = warnings != null ? List.copyOf(warnings) : Collections.emptyList();
    }

    /**
     * Factory method representing an empty intelligence model for absent or blank job descriptions.
     *
     * @param jobId UUID of the job
     * @return empty JobDescriptionIntelligence with DESCRIPTION_EMPTY warning
     */
    public static JobDescriptionIntelligence empty(UUID jobId) {
        return new JobDescriptionIntelligence(
                jobId,
                false,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(JobIntelligenceWarning.DESCRIPTION_EMPTY)
        );
    }
}
