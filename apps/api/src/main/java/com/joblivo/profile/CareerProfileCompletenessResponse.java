package com.joblivo.profile;

/**
 * Deterministic completeness summary for a Master Career Profile.
 * Evaluates completion across 7 equal-weight sections without AI or subjective scoring.
 */
public record CareerProfileCompletenessResponse(
        int completionPercentage,
        SectionCompletenessResponse coreDetails,
        SectionCompletenessResponse workExperience,
        SectionCompletenessResponse skills,
        SectionCompletenessResponse projects,
        SectionCompletenessResponse education,
        SectionCompletenessResponse certifications,
        SectionCompletenessResponse achievements
) {
    public static final int TOTAL_SECTIONS = 7;

    /**
     * Calculates the deterministic completeness summary based on the profile core fields
     * and counts of associated child records.
     */
    public static CareerProfileCompletenessResponse calculate(
            CareerProfile profile,
            int workExperienceCount,
            int skillCount,
            int projectCount,
            int educationCount,
            int certificationCount,
            int achievementCount
    ) {
        int coreFieldCount = countMeaningfulCoreFields(profile);
        boolean coreCompleted = coreFieldCount > 0;
        SectionCompletenessResponse coreDetailsSection = SectionCompletenessResponse.of(coreCompleted, coreFieldCount);

        SectionCompletenessResponse workExperienceSection = SectionCompletenessResponse.of(workExperienceCount > 0, workExperienceCount);
        SectionCompletenessResponse skillsSection = SectionCompletenessResponse.of(skillCount > 0, skillCount);
        SectionCompletenessResponse projectsSection = SectionCompletenessResponse.of(projectCount > 0, projectCount);
        SectionCompletenessResponse educationSection = SectionCompletenessResponse.of(educationCount > 0, educationCount);
        SectionCompletenessResponse certificationsSection = SectionCompletenessResponse.of(certificationCount > 0, certificationCount);
        SectionCompletenessResponse achievementsSection = SectionCompletenessResponse.of(achievementCount > 0, achievementCount);

        int completedSections = 0;
        if (coreDetailsSection.completed()) {
            completedSections++;
        }
        if (workExperienceSection.completed()) {
            completedSections++;
        }
        if (skillsSection.completed()) {
            completedSections++;
        }
        if (projectsSection.completed()) {
            completedSections++;
        }
        if (educationSection.completed()) {
            completedSections++;
        }
        if (certificationsSection.completed()) {
            completedSections++;
        }
        if (achievementsSection.completed()) {
            completedSections++;
        }

        int completionPercentage = (completedSections * 100) / TOTAL_SECTIONS;

        return new CareerProfileCompletenessResponse(
                completionPercentage,
                coreDetailsSection,
                workExperienceSection,
                skillsSection,
                projectsSection,
                educationSection,
                certificationsSection,
                achievementsSection
        );
    }

    /**
     * Counts the number of meaningful populated core profile fields (0 to 8).
     */
    public static int countMeaningfulCoreFields(CareerProfile profile) {
        if (profile == null) {
            return 0;
        }
        int count = 0;
        if (isNotBlank(profile.getProfessionalHeadline())) {
            count++;
        }
        if (isNotBlank(profile.getCurrentTitle())) {
            count++;
        }
        if (isNotBlank(profile.getCurrentCompany())) {
            count++;
        }
        if (profile.getTotalExperienceMonths() != null) {
            count++;
        }
        if (isNotBlank(profile.getCurrentLocation())) {
            count++;
        }
        if (isNotBlank(profile.getPreferredWorkLocation())) {
            count++;
        }
        if (profile.getPreferredWorkMode() != null) {
            count++;
        }
        if (profile.getNoticePeriodDays() != null) {
            count++;
        }
        return count;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
