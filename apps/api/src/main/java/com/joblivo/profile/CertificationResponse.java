package com.joblivo.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public response projection for Master Career Profile certifications.
 * Exposes factual certification details without leaking persistence internals.
 */
public record CertificationResponse(
        UUID id,
        String certificationName,
        String issuingOrganization,
        String credentialId,
        String credentialUrl,
        LocalDate issueDate,
        LocalDate expirationDate,
        boolean doesNotExpire,
        String description,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public static CertificationResponse from(CareerProfileCertification cert) {
        return new CertificationResponse(
                cert.getId(),
                cert.getCertificationName(),
                cert.getIssuingOrganization(),
                cert.getCredentialId(),
                cert.getCredentialUrl(),
                cert.getIssueDate(),
                cert.getExpirationDate(),
                cert.isDoesNotExpire(),
                cert.getDescription(),
                cert.getDisplayOrder(),
                cert.getCreatedAt(),
                cert.getUpdatedAt()
        );
    }
}
