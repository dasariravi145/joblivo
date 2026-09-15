package com.joblivo.profile;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Validated request record for creating and updating Master Career Profile certifications.
 */
public record CertificationRequest(
        @NotBlank(message = "Certification name is required")
        @Size(max = 150, message = "Certification name must not exceed 150 characters")
        String certificationName,

        @NotBlank(message = "Issuing organization is required")
        @Size(max = 150, message = "Issuing organization must not exceed 150 characters")
        String issuingOrganization,

        @Size(max = 100, message = "Credential ID must not exceed 100 characters")
        String credentialId,

        @Size(max = 500, message = "Credential URL must not exceed 500 characters")
        @Pattern(
                regexp = "^(https?://.+)?$",
                message = "Credential URL must be a valid HTTP or HTTPS URL"
        )
        String credentialUrl,

        LocalDate issueDate,

        LocalDate expirationDate,

        Boolean doesNotExpire,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Min(value = 0, message = "Display order must be greater than or equal to 0")
        Integer displayOrder
) {
    public CertificationRequest {
        certificationName = certificationName != null ? certificationName.trim() : null;
        issuingOrganization = issuingOrganization != null ? issuingOrganization.trim() : null;
        credentialId = (credentialId != null && !credentialId.isBlank()) ? credentialId.trim() : null;
        credentialUrl = (credentialUrl != null && !credentialUrl.isBlank()) ? credentialUrl.trim() : null;
        doesNotExpire = Boolean.TRUE.equals(doesNotExpire);
        description = (description != null && !description.isBlank()) ? description.trim() : null;
        displayOrder = displayOrder != null ? displayOrder : 0;
    }

    @AssertTrue(message = "Expiration date cannot be before issue date")
    public boolean isDateRangeValid() {
        if (issueDate == null || expirationDate == null) {
            return true;
        }
        return !expirationDate.isBefore(issueDate);
    }

    @AssertTrue(message = "Expiration date must be null when does not expire is true")
    public boolean isDoesNotExpireValid() {
        if (Boolean.TRUE.equals(doesNotExpire)) {
            return expirationDate == null;
        }
        return true;
    }
}
