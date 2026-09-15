package com.joblivo.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Validated request payload for creating or updating a skill/technology entry.
 * Normalizes string inputs and validates category, proficiency, and non-negative experience constraints.
 */
public record SkillRequest(
        @NotBlank(message = "Skill name is required")
        @Size(max = 100, message = "Skill name cannot exceed 100 characters")
        String name,

        @NotNull(message = "Category is required")
        SkillCategory category,

        @NotNull(message = "Proficiency is required")
        SkillProficiency proficiency,

        @PositiveOrZero(message = "Years of experience must be greater than or equal to 0")
        BigDecimal yearsOfExperience,

        LocalDate lastUsedDate,

        @PositiveOrZero(message = "Display order must be greater than or equal to 0")
        Integer displayOrder
) {
    public SkillRequest {
        name = (name != null) ? name.trim() : null;
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }
}
