package com.joblivo.truth.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable reference to a supporting factual anchor in the Master Career Profile.
 * References the source entity without duplicating career data.
 *
 * @param sourceType  the section of the career profile containing the evidence
 * @param referenceId the unique identifier of the supporting record (if available)
 * @param summary     a safe, non-sensitive summary of the factual anchor (e.g. "Skill: AWS", "Company: ACME Corp")
 */
public record EvidenceReference(
        EvidenceSourceType sourceType,
        String referenceId,
        String summary
) {
    public EvidenceReference {
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        summary = summary != null ? summary.trim() : "";
        referenceId = referenceId != null ? referenceId.trim() : null;
    }

    public static EvidenceReference of(EvidenceSourceType sourceType, UUID id, String summary) {
        return new EvidenceReference(sourceType, id != null ? id.toString() : null, summary);
    }

    public static EvidenceReference of(EvidenceSourceType sourceType, String summary) {
        return new EvidenceReference(sourceType, null, summary);
    }
}
