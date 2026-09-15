package com.joblivo.profile;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Validated request payload for creating or updating an education record.
 * Normalizes string inputs and validates logical date ranges and active study status rules.
 */
public record EducationRequest(
        @NotBlank(message = "Institution name is required")
        @Size(max = 150, message = "Institution name cannot exceed 150 characters")
        String institutionName,

        @Size(max = 100, message = "Degree cannot exceed 100 characters")
        String degree,

        @Size(max = 100, message = "Field of study cannot exceed 100 characters")
        String fieldOfStudy,

        @NotNull(message = "Education level is required")
        EducationLevel educationLevel,

        LocalDate startDate,

        LocalDate endDate,

        Boolean currentlyStudying,

        @Size(max = 50, message = "Grade cannot exceed 50 characters")
        String grade,

        @Size(max = 150, message = "Location cannot exceed 150 characters")
        String location,

        @Size(max = 5000, message = "Description cannot exceed 5000 characters")
        String description,

        @PositiveOrZero(message = "Display order must be greater than or equal to 0")
        Integer displayOrder
) {
    public EducationRequest {
        institutionName = (institutionName != null) ? institutionName.trim() : null;
        degree = (degree != null && !degree.isBlank()) ? degree.trim() : null;
        fieldOfStudy = (fieldOfStudy != null && !fieldOfStudy.isBlank()) ? fieldOfStudy.trim() : null;
        grade = (grade != null && !grade.isBlank()) ? grade.trim() : null;
        location = (location != null && !location.isBlank()) ? location.trim() : null;
        description = (description != null && !description.isBlank()) ? description.trim() : null;
        if (currentlyStudying == null) {
            currentlyStudying = Boolean.FALSE;
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

    @AssertTrue(message = "End date must be null when currently studying is true")
    public boolean isCurrentlyStudyingValid() {
        if (Boolean.TRUE.equals(currentlyStudying)) {
            return endDate == null;
        }
        return true;
    }
}
