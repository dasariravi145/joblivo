package com.joblivo.profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public response model for skill/technology competencies.
 * Contains only safe, factual competency information and excludes internal persistence details.
 */
public record SkillResponse(
        UUID id,
        String name,
        SkillCategory category,
        SkillProficiency proficiency,
        BigDecimal yearsOfExperience,
        LocalDate lastUsedDate,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public static SkillResponse from(CareerProfileSkill skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getName(),
                skill.getCategory(),
                skill.getProficiency(),
                skill.getYearsOfExperience(),
                skill.getLastUsedDate(),
                skill.getDisplayOrder(),
                skill.getCreatedAt(),
                skill.getUpdatedAt()
        );
    }
}
