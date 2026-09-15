package com.joblivo.auth;

import com.joblivo.user.AuthProvider;
import com.joblivo.user.User;
import com.joblivo.user.UserStatus;

import java.util.UUID;

/**
 * Safe, client-facing response payload returned upon successful authentication.
 * Exposes identity metadata without exposing credentials, hashes, or security internals.
 */
public record LoginResponse(
    UUID id,
    String email,
    String displayName,
    AuthProvider provider,
    UserStatus status
) {
    public static LoginResponse of(User user, AuthProvider provider) {
        return new LoginResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            provider,
            user.getStatus()
        );
    }
}
