package com.joblivo.truth.model;

/**
 * Meaningfully defined semantic certainty level for truth evaluations.
 * <p>
 * Explicit design rule: Joblivo rejects fake floating-point confidence scores (e.g. 0.95 or 0.82)
 * that misrepresent probabilistic or heuristic certainty as factual mathematical proof.
 * This enum provides concrete, auditable semantic tiers.
 */
public enum TruthConfidence {

    /**
     * Exact factual identity verified directly against verified profile anchor.
     */
    EXACT_MATCH,

    /**
     * Direct logical implication or mathematical derivation without introducing new factual claims.
     */
    LOGICAL_DERIVATION,

    /**
     * Partial or ambiguous match with some supporting context, but requiring human verification.
     */
    PARTIAL_MATCH,

    /**
     * Zero supporting factual anchors found.
     */
    NONE
}
