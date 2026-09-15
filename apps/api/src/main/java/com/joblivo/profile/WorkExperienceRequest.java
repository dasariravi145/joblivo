package com.joblivo.profile;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Validated request payload for creating or updating a work experience record.
 * Normalizes string inputs and validates logical date and current employment rules.
 */
public record WorkExperienceRequest(
        @NotBlank(message = "Company name is required")
        @Size(max = 100, message = "Company name cannot exceed 100 characters")
        String companyName,

        @NotBlank(message = "Job title is required")
        @Size(max = 100, message = "Job title cannot exceed 100 characters")
        String jobTitle,

        @NotNull(message = "Employment type is required")
        EmploymentType employmentType,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        LocalDate endDate,

        Boolean currentlyWorking,

        @Size(max = 100, message = "Location cannot exceed 100 characters")
        String location,

        @Size(max = 5000, message = "Description cannot exceed 5000 characters")
        String description,

        @PositiveOrZero(message = "Display order must be greater than or equal to 0")
        Integer displayOrder
) {
    public WorkExperienceRequest {
        companyName = (companyName != null) ? companyName.trim() : null;
        jobTitle = (jobTitle != null) ? jobTitle.trim() : null;
        location = (location != null && !location.isBlank()) ? location.trim() : null;
        description = (description != null && !description.isBlank()) ? description.trim() : null;
        if (currentlyWorking == null) {
            currentlyWorking = Boolean.FALSE;
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

    @AssertTrue(message = "End date must be null when currently working is true")
    public boolean isCurrentlyWorkingValid() {
        if (Boolean.TRUE.equals(currentlyWorking)) {
            return endDate == null;
        }
        return true;
    }
}
