package com.joblivo.truth.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable evaluation result produced by the {@link com.joblivo.truth.TruthEngine}.
 * Contains evidence status, protection level, semantic confidence, reasoning, and supporting evidence references.
 *
 * @param claim                    the claim that was evaluated
 * @param status                   the evidence status (VERIFIED, DERIVED, NEEDS_CONFIRMATION, UNSUPPORTED)
 * @param protectionLevel          operational protection directive for downstream consumers
 * @param confidence               semantic confidence tier (not a pseudo-numeric percentage)
 * @param reason                   transparent domain explanation of the evaluation outcome
 * @param requiresUserConfirmation whether the claim requires human confirmation before downstream usage
 * @param evidenceReferences       references to supporting facts in the user's Master Career Profile
 * @param correlationId            safe tracking identifier for audit and observability
 */
public record TruthEvaluation(
        CareerClaim claim,
        EvidenceStatus status,
        TruthProtectionLevel protectionLevel,
        TruthConfidence confidence,
        String reason,
        boolean requiresUserConfirmation,
        List<EvidenceReference> evidenceReferences,
        String correlationId
) {
    public TruthEvaluation {
        Objects.requireNonNull(claim, "claim must not be null");
        Objects.requireNonNull(status, "status must not be null");
        protectionLevel = protectionLevel != null ? protectionLevel : TruthProtectionLevel.fromStatus(status);
        confidence = confidence != null ? confidence : TruthConfidence.NONE;
        reason = reason != null ? reason.trim() : "";
        evidenceReferences = evidenceReferences != null ? Collections.unmodifiableList(evidenceReferences) : Collections.emptyList();
    }

    public boolean isSafeToUse() {
        return protectionLevel == TruthProtectionLevel.SAFE_TO_USE;
    }

    public boolean requiresConfirmation() {
        return protectionLevel == TruthProtectionLevel.REQUIRES_CONFIRMATION;
    }

    public boolean isDoNotUse() {
        return protectionLevel == TruthProtectionLevel.DO_NOT_USE;
    }

    public boolean isVerified() {
        return status == EvidenceStatus.VERIFIED;
    }

    public boolean isDerived() {
        return status == EvidenceStatus.DERIVED;
    }

    public boolean isUnsupported() {
        return status == EvidenceStatus.UNSUPPORTED;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TruthEvaluation verified(CareerClaim claim, String reason, List<EvidenceReference> evidence) {
        return builder()
                .claim(claim)
                .status(EvidenceStatus.VERIFIED)
                .protectionLevel(TruthProtectionLevel.SAFE_TO_USE)
                .confidence(TruthConfidence.EXACT_MATCH)
                .reason(reason)
                .requiresUserConfirmation(false)
                .evidenceReferences(evidence)
                .correlationId(claim.correlationId())
                .build();
    }

    public static TruthEvaluation derived(CareerClaim claim, String reason, List<EvidenceReference> evidence) {
        return builder()
                .claim(claim)
                .status(EvidenceStatus.DERIVED)
                .protectionLevel(TruthProtectionLevel.SAFE_TO_USE)
                .confidence(TruthConfidence.LOGICAL_DERIVATION)
                .reason(reason)
                .requiresUserConfirmation(false)
                .evidenceReferences(evidence)
                .correlationId(claim.correlationId())
                .build();
    }

    public static TruthEvaluation needsConfirmation(CareerClaim claim, String reason, List<EvidenceReference> partialEvidence) {
        return builder()
                .claim(claim)
                .status(EvidenceStatus.NEEDS_CONFIRMATION)
                .protectionLevel(TruthProtectionLevel.REQUIRES_CONFIRMATION)
                .confidence(TruthConfidence.PARTIAL_MATCH)
                .reason(reason)
                .requiresUserConfirmation(true)
                .evidenceReferences(partialEvidence)
                .correlationId(claim.correlationId())
                .build();
    }

    public static TruthEvaluation unsupported(CareerClaim claim, String reason) {
        return builder()
                .claim(claim)
                .status(EvidenceStatus.UNSUPPORTED)
                .protectionLevel(TruthProtectionLevel.DO_NOT_USE)
                .confidence(TruthConfidence.NONE)
                .reason(reason)
                .requiresUserConfirmation(false)
                .evidenceReferences(List.of())
                .correlationId(claim.correlationId())
                .build();
    }

    public static class Builder {
        private CareerClaim claim;
        private EvidenceStatus status;
        private TruthProtectionLevel protectionLevel;
        private TruthConfidence confidence;
        private String reason;
        private boolean requiresUserConfirmation;
        private List<EvidenceReference> evidenceReferences = List.of();
        private String correlationId;

        public Builder claim(CareerClaim claim) {
            this.claim = claim;
            return this;
        }

        public Builder status(EvidenceStatus status) {
            this.status = status;
            return this;
        }

        public Builder protectionLevel(TruthProtectionLevel protectionLevel) {
            this.protectionLevel = protectionLevel;
            return this;
        }

        public Builder confidence(TruthConfidence confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder requiresUserConfirmation(boolean requiresUserConfirmation) {
            this.requiresUserConfirmation = requiresUserConfirmation;
            return this;
        }

        public Builder evidenceReferences(List<EvidenceReference> evidenceReferences) {
            this.evidenceReferences = evidenceReferences;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public TruthEvaluation build() {
            return new TruthEvaluation(
                    claim, status, protectionLevel, confidence, reason,
                    requiresUserConfirmation, evidenceReferences, correlationId
            );
        }
    }
}
