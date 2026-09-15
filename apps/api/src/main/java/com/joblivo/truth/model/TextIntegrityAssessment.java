package com.joblivo.truth.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Assessment result evaluating the factual integrity of text rewritten or transformed by AI.
 * Enforces the core rule: AI may improve wording or clarity, but must never introduce unsupported professional facts.
 *
 * @param sourceText            the original user-provided text
 * @param proposedText          the rewritten or generated text proposed by AI
 * @param status                the aggregate evidence status
 * @param protectionLevel       operational directive (SAFE_TO_USE, REQUIRES_CONFIRMATION, DO_NOT_USE)
 * @param evaluations           individual claim evaluations evaluated from the proposed text
 * @param hasUnsupportedFacts   true if any extracted or introduced claim is UNSUPPORTED
 * @param reason                overall integrity summary
 */
public record TextIntegrityAssessment(
        String sourceText,
        String proposedText,
        EvidenceStatus status,
        TruthProtectionLevel protectionLevel,
        List<TruthEvaluation> evaluations,
        boolean hasUnsupportedFacts,
        String reason
) {
    public TextIntegrityAssessment {
        sourceText = sourceText != null ? sourceText.trim() : "";
        proposedText = proposedText != null ? proposedText.trim() : "";
        Objects.requireNonNull(status, "status must not be null");
        protectionLevel = protectionLevel != null ? protectionLevel : TruthProtectionLevel.fromStatus(status);
        evaluations = evaluations != null ? Collections.unmodifiableList(evaluations) : Collections.emptyList();
        reason = reason != null ? reason.trim() : "";
    }

    public boolean isSafeToUse() {
        return protectionLevel == TruthProtectionLevel.SAFE_TO_USE && !hasUnsupportedFacts;
    }

    public static TextIntegrityAssessment verified(String sourceText, String proposedText, List<TruthEvaluation> evaluations, String reason) {
        return new TextIntegrityAssessment(
                sourceText, proposedText, EvidenceStatus.VERIFIED, TruthProtectionLevel.SAFE_TO_USE,
                evaluations, false, reason
        );
    }

    public static TextIntegrityAssessment derived(String sourceText, String proposedText, List<TruthEvaluation> evaluations, String reason) {
        return new TextIntegrityAssessment(
                sourceText, proposedText, EvidenceStatus.DERIVED, TruthProtectionLevel.SAFE_TO_USE,
                evaluations, false, reason
        );
    }

    public static TextIntegrityAssessment needsConfirmation(String sourceText, String proposedText, List<TruthEvaluation> evaluations, String reason) {
        return new TextIntegrityAssessment(
                sourceText, proposedText, EvidenceStatus.NEEDS_CONFIRMATION, TruthProtectionLevel.REQUIRES_CONFIRMATION,
                evaluations, false, reason
        );
    }

    public static TextIntegrityAssessment unsupported(String sourceText, String proposedText, List<TruthEvaluation> evaluations, String reason) {
        return new TextIntegrityAssessment(
                sourceText, proposedText, EvidenceStatus.UNSUPPORTED, TruthProtectionLevel.DO_NOT_USE,
                evaluations, true, reason
        );
    }
}
