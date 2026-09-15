package com.joblivo.profile;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Client-safe response record representing a persisted Master Career Profile.
 * Strictly exposes career identity, professional targeting, and timestamp metadata
 * without sensitive internals or credential data.
 */
public record CareerProfileResponse(
    UUID id,
    UUID userId,
    String professionalHeadline,
    String currentTitle,
    String currentCompany,
    Integer totalExperienceMonths,
    String currentLocation,
    String preferredWorkLocation,
    WorkMode preferredWorkMode,
    Integer noticePeriodDays,
    Instant createdAt,
    Instant updatedAt
) {
    public static CareerProfileResponse fromEntity(CareerProfile profile) {
        Objects.requireNonNull(profile, "CareerProfile must not be null");
        UUID userId = (profile.getUser() != null) ? profile.getUser().getId() : null;
        return new CareerProfileResponse(
            profile.getId(),
            userId,
            profile.getProfessionalHeadline(),
            profile.getCurrentTitle(),
            profile.getCurrentCompany(),
            profile.getTotalExperienceMonths(),
            profile.getCurrentLocation(),
            profile.getPreferredWorkLocation(),
            profile.getPreferredWorkMode(),
            profile.getNoticePeriodDays(),
            profile.getCreatedAt(),
            profile.getUpdatedAt()
        );
    }
}
