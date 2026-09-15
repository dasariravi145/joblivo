package com.joblivo.truth;

import com.joblivo.truth.context.CareerFactContext;
import com.joblivo.truth.model.CareerClaim;
import com.joblivo.truth.model.ClaimCategory;
import com.joblivo.truth.model.TextIntegrityAssessment;
import com.joblivo.truth.model.TruthEvaluation;

import java.util.List;

/**
 * Provider-neutral core domain service protecting factual career integrity across Joblivo.
 * <p>
 * Ensures that all future AI features (resumes, job applications, LinkedIn content, interview prep)
 * remain strictly grounded in authentic, user-provided career facts and never fabricate credentials.
 * <p>
 * Does not depend on external AI provider SDKs, HTTP clients, web controllers, or database persistence entities.
 */
public interface TruthEngine {

    /**
     * Evaluates a single career claim against the user's factual career context.
     *
     * @param claim   the factual career claim to evaluate
     * @param context the authentic career facts belonging to the user
     * @return a structured, provider-neutral {@link TruthEvaluation}
     */
    TruthEvaluation evaluate(CareerClaim claim, CareerFactContext context);

    /**
     * Evaluates a batch of career claims against the user's factual career context.
     *
     * @param claims  the list of claims to evaluate
     * @param context the authentic career facts belonging to the user
     * @return an immutable list of structured {@link TruthEvaluation}s
     */
    List<TruthEvaluation> evaluateAll(List<CareerClaim> claims, CareerFactContext context);

    /**
     * Evaluates whether an AI-proposed text rewrite introduced unsupported career facts
     * when compared against original source text and the user's career context.
     *
     * @param sourceText   the user's original statement or draft
     * @param proposedText the AI-generated or rewritten statement
     * @param context      the authentic career facts belonging to the user
     * @return a {@link TextIntegrityAssessment} recording evidence status and any detected integrity breaches
     */
    TextIntegrityAssessment assessTransformation(String sourceText, String proposedText, CareerFactContext context);

    /**
     * Convenience method returning whether a claim is safe for inclusion in generated outputs
     * without requiring user confirmation.
     *
     * @param claim   the claim to evaluate
     * @param context the authentic career facts belonging to the user
     * @return true if {@link com.joblivo.truth.model.TruthProtectionLevel#SAFE_TO_USE}
     */
    default boolean isSafeToUse(CareerClaim claim, CareerFactContext context) {
        return evaluate(claim, context).isSafeToUse();
    }

    /**
     * Convenience overload evaluating raw text under a specified category.
     *
     * @param text     the claim statement
     * @param category the category of the claim
     * @param context  the user's career facts
     * @return a structured {@link TruthEvaluation}
     */
    default TruthEvaluation evaluateText(String text, ClaimCategory category, CareerFactContext context) {
        return evaluate(CareerClaim.of(text, category), context);
    }
}
