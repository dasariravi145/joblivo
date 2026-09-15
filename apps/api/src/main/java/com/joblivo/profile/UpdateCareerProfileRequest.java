package com.joblivo.profile;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Validated request record for updating a user's Master Career Profile core details.
 * Trims text fields and normalizes empty inputs to null.
 * Strictly excludes any userId parameter to safeguard profile ownership.
 */
public record UpdateCareerProfileRequest(
    @Size(max = 200, message = "Professional headline must not exceed 200 characters")
    String professionalHeadline,

    @Size(max = 100, message = "Current title must not exceed 100 characters")
    String currentTitle,

    @Size(max = 100, message = "Current company must not exceed 100 characters")
    String currentCompany,

    @PositiveOrZero(message = "Total experience months must not be negative")
    Integer totalExperienceMonths,

    @Size(max = 100, message = "Current location must not exceed 100 characters")
    String currentLocation,

    @Size(max = 100, message = "Preferred work location must not exceed 100 characters")
    String preferredWorkLocation,

    WorkMode preferredWorkMode,

    @PositiveOrZero(message = "Notice period days must not be negative")
    Integer noticePeriodDays
) {
    public UpdateCareerProfileRequest {
        professionalHeadline = trimToNull(professionalHeadline);
        currentTitle = trimToNull(currentTitle);
        currentCompany = trimToNull(currentCompany);
        currentLocation = trimToNull(currentLocation);
        preferredWorkLocation = trimToNull(preferredWorkLocation);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
