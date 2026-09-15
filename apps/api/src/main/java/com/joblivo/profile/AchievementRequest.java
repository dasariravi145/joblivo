package com.joblivo.profile;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Validated request record for creating and updating Master Career Profile achievements.
 */
public record AchievementRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must not exceed 150 characters")
        String title,

        @NotNull(message = "Achievement type is required")
        AchievementType achievementType,

        @Size(max = 150, message = "Issuing organization must not exceed 150 characters")
        String issuingOrganization,

        LocalDate achievementDate,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Size(max = 500, message = "URL must not exceed 500 characters")
        @Pattern(
                regexp = "^(https?://.+)?$",
                message = "URL must be a valid HTTP or HTTPS URL"
        )
        String url,

        @Min(value = 0, message = "Display order must be greater than or equal to 0")
        Integer displayOrder
) {
    public AchievementRequest {
        title = title != null ? title.trim() : null;
        issuingOrganization = (issuingOrganization != null && !issuingOrganization.isBlank()) ? issuingOrganization.trim() : null;
        description = (description != null && !description.isBlank()) ? description.trim() : null;
        url = (url != null && !url.isBlank()) ? url.trim() : null;
        displayOrder = displayOrder != null ? displayOrder : 0;
    }
}
