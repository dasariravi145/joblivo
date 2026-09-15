package com.joblivo.profile;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Validated request payload for creating or updating a project record.
 * Normalizes string inputs and validates logical date ranges, URL format, and active status rules.
 */
public record ProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(max = 100, message = "Project name cannot exceed 100 characters")
        String projectName,

        @NotNull(message = "Project type is required")
        ProjectType projectType,

        @Size(max = 100, message = "Role cannot exceed 100 characters")
        String role,

        @Size(max = 5000, message = "Description cannot exceed 5000 characters")
        String description,

        LocalDate startDate,

        LocalDate endDate,

        Boolean currentlyActive,

        @Size(max = 500, message = "Project URL cannot exceed 500 characters")
        @Pattern(regexp = "^https?://.+", message = "Project URL must be a valid HTTP or HTTPS URL")
        String projectUrl,

        @PositiveOrZero(message = "Display order must be greater than or equal to 0")
        Integer displayOrder
) {
    public ProjectRequest {
        projectName = (projectName != null) ? projectName.trim() : null;
        role = (role != null && !role.isBlank()) ? role.trim() : null;
        description = (description != null && !description.isBlank()) ? description.trim() : null;
        projectUrl = (projectUrl != null && !projectUrl.isBlank()) ? projectUrl.trim() : null;
        if (currentlyActive == null) {
            currentlyActive = Boolean.FALSE;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    @AssertTrue(message = "End date cannot be before start date")
    public boolean isDateRangeValid() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "End date must be null when currently active is true")
    public boolean isCurrentlyActiveValid() {
        if (Boolean.TRUE.equals(currentlyActive)) {
            return endDate == null;
        }
        return true;
    }
}
