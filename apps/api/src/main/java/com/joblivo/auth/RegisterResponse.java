package com.joblivo.auth;

import com.joblivo.user.User;
import com.joblivo.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe, client-facing response payload for successful registration.
 * Exposes account identifiers and status without exposing passwords, hashes, tokens, or JPA entities.
 */
public record RegisterResponse(
    UUID id,
    String email,
    String displayName,
    UserStatus status,
    Instant createdAt
) {
    public static RegisterResponse fromEntity(User user) {
        return new RegisterResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getStatus(),
            user.getCreatedAt()
        );
    }
}
