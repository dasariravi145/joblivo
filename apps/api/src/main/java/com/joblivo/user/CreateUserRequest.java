package com.joblivo.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload model for creating a new user.
 * Restricted strictly to caller-supplied fields (email and displayName).
 */
public record CreateUserRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    String email,

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name cannot exceed 100 characters")
    String displayName
) {
}
