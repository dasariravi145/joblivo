package com.joblivo.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for email/password authentication.
 * Passwords are treated as raw, case-sensitive inputs and are never altered.
 */
public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    String email,

    @NotBlank(message = "Password is required")
    String password
) {
    @Override
    public String toString() {
        return "LoginRequest[" +
                "email=" + email +
                ", password=[PROTECTED]" +
                "]";
    }
}
