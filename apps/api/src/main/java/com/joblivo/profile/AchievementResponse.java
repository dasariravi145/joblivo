package com.joblivo.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public response projection for Master Career Profile achievements.
 * Exposes factual achievement details without leaking persistence internals.
 */
public record AchievementResponse(
        UUID id,
        String title,
        AchievementType achievementType,
        String description,
        LocalDate achievementDate,
        String issuingOrganization,
        String url,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public static AchievementResponse from(CareerProfileAchievement achievement) {
        return new AchievementResponse(
                achievement.getId(),
                achievement.getTitle(),
                achievement.getAchievementType(),
                achievement.getDescription(),
                achievement.getAchievementDate(),
                achievement.getIssuingOrganization(),
                achievement.getUrl(),
                achievement.getDisplayOrder(),
                achievement.getCreatedAt(),
                achievement.getUpdatedAt()
        );
    }
}
