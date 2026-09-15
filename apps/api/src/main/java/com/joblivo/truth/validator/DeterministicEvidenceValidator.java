package com.joblivo.truth.validator;

import com.joblivo.truth.context.CareerFactContext;
import com.joblivo.truth.model.CareerClaim;
import com.joblivo.truth.model.TruthEvaluation;

/**
 * Internal SPI defining conservative, deterministic validation of career claims
 * against a user's verified {@link CareerFactContext}.
 * <p>
 * Deterministic validation executes strictly in-memory without making external network calls
 * or probabilistic model invocations.
 */
public interface DeterministicEvidenceValidator {

    /**
     * Deterministically evaluates a factual career claim against the user's career context.
     *
     * @param claim   the claim to evaluate
     * @param context the user's factual career context
     * @return a structured truth evaluation
     */
    TruthEvaluation validate(CareerClaim claim, CareerFactContext context);
}
