package com.joblivo.profile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Unified read model DTO for a user's Master Career Profile.
 * Aggregates core career identity details, child collections, and completeness status.
 */
public record MasterCareerProfileResponse(
        UUID id,
        String professionalHeadline,
        String currentTitle,
        String currentCompany,
        Integer totalExperienceMonths,
        String currentLocation,
        String preferredWorkLocation,
        WorkMode preferredWorkMode,
        Integer noticePeriodDays,
        Instant createdAt,
        Instant updatedAt,
        List<WorkExperienceResponse> workExperiences,
        List<SkillResponse> skills,
        List<ProjectResponse> projects,
        List<EducationResponse> education,
        List<CertificationResponse> certifications,
        List<AchievementResponse> achievements,
        CareerProfileCompletenessResponse completeness
) {
}
