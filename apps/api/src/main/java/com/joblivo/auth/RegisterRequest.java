package com.joblivo.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for email/password registration.
 * Restricts client control strictly to caller-supplied registration inputs.
 */
public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    String email,

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name cannot exceed 100 characters")
    String displayName,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    String password,

    @NotBlank(message = "Password confirmation is required")
    String confirmPassword
) {
    @Override
    public String toString() {
        return "RegisterRequest[" +
                "email=" + email +
                ", displayName=" + displayName +
                ", password=[PROTECTED]" +
                ", confirmPassword=[PROTECTED]" +
                "]";
    }
}
