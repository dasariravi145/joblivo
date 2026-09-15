package com.joblivo.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Response model representing a persisted user in the application layer.
 */
public record UserResponse(
    UUID id,
    String email,
    String displayName,
    UserStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getStatus(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
