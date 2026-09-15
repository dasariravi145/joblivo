package com.joblivo.truth.model;

/**
 * Domain-level operational protection level dictating how downstream consumers (e.g. resume generators,
 * application assistants, interview prep) may handle an evaluated claim.
 */
public enum TruthProtectionLevel {

    /**
     * Factual integrity established. The claim is {@link EvidenceStatus#VERIFIED} or {@link EvidenceStatus#DERIVED}
     * and safe for inclusion in generated career artifacts.
     */
    SAFE_TO_USE,

    /**
     * Insufficient factual evidence. The claim is {@link EvidenceStatus#NEEDS_CONFIRMATION}.
     * Downstream systems must flag this claim to the user for explicit confirmation before use.
     */
    REQUIRES_CONFIRMATION,

    /**
     * Unsubstantiated or contradicted. The claim is {@link EvidenceStatus#UNSUPPORTED}.
     * Downstream systems must exclude this claim and must never present it as fact.
     */
    DO_NOT_USE;

    public static TruthProtectionLevel fromStatus(EvidenceStatus status) {
        if (status == null) {
            return DO_NOT_USE;
        }
        return switch (status) {
            case VERIFIED, DERIVED -> SAFE_TO_USE;
            case NEEDS_CONFIRMATION -> REQUIRES_CONFIRMATION;
            case UNSUPPORTED -> DO_NOT_USE;
        };
    }
}
