package com.joblivo.truth;

import com.joblivo.profile.SkillResponse;
import com.joblivo.truth.context.CareerFactContext;
import com.joblivo.truth.exception.CrossUserContextException;
import com.joblivo.truth.exception.InvalidCareerContextException;
import com.joblivo.truth.exception.InvalidClaimException;
import com.joblivo.truth.model.CareerClaim;
import com.joblivo.truth.model.ClaimCategory;
import com.joblivo.truth.model.EvidenceStatus;
import com.joblivo.truth.model.TextIntegrityAssessment;
import com.joblivo.truth.model.TruthEvaluation;
import com.joblivo.truth.model.TruthProtectionLevel;
import com.joblivo.truth.validator.DeterministicEvidenceValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production implementation of {@link TruthEngine}.
 * Orchestrates deterministic factual validation against the user's authentic {@link CareerFactContext}.
 * <p>
 * Enforces:
 * <ul>
 *   <li>User boundary isolation: prevents evaluating one user's claims against another's profile.</li>
 *   <li>Safe observability: logs only evaluation metadata, never dumping raw personal career profiles.</li>
 *   <li>Strict factual rewrite protection: prevents AI rewrites from introducing unverified technologies or metrics.</li>
 * </ul>
 */
@Service
public class DefaultTruthEngine implements TruthEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultTruthEngine.class);

    private static final Pattern METRIC_TOKEN_PATTERN =
            Pattern.compile("\\b(\\d+(?:\\.\\d+)?(?:[kmbt]|\\+)?|\\$\\d+(?:\\.\\d+)?(?:[kmbt])?)\\b", Pattern.CASE_INSENSITIVE);

    private final DeterministicEvidenceValidator deterministicValidator;

    public DefaultTruthEngine(DeterministicEvidenceValidator deterministicValidator) {
        this.deterministicValidator = Objects.requireNonNull(deterministicValidator, "deterministicValidator must not be null");
    }

    @Override
    public TruthEvaluation evaluate(CareerClaim claim, CareerFactContext context) {
        validateInputs(claim, context);

        TruthEvaluation evaluation = deterministicValidator.validate(claim, context);

        log.info("Truth evaluation completed [userId={}, correlationId={}, category={}, status={}, protectionLevel={}, confidence={}]",
                context.userId(), evaluation.correlationId(), claim.category(),
                evaluation.status(), evaluation.protectionLevel(), evaluation.confidence());

        return evaluation;
    }

    @Override
    public List<TruthEvaluation> evaluateAll(List<CareerClaim> claims, CareerFactContext context) {
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }
        if (context == null || context.userId() == null) {
            throw new InvalidCareerContextException("CareerFactContext requires a non-null userId");
        }

        return claims.stream()
                .map(claim -> evaluate(claim, context))
                .toList();
    }

    @Override
    public TextIntegrityAssessment assessTransformation(String sourceText, String proposedText, CareerFactContext context) {
        if (context == null || context.userId() == null) {
            throw new InvalidCareerContextException("CareerFactContext requires a non-null userId");
        }

        String safeSource = sourceText != null ? sourceText.trim() : "";
        String safeProposed = proposedText != null ? proposedText.trim() : "";

        if (safeProposed.isBlank()) {
            return TextIntegrityAssessment.verified(safeSource, safeProposed, List.of(), "Empty proposed text contains no claims");
        }

        List<TruthEvaluation> evaluations = new ArrayList<>();
        List<String> violations = new ArrayList<>();

        // 1. Check for newly introduced metrics (numbers, scales, percentages) not present in source text
        Matcher metricMatcher = METRIC_TOKEN_PATTERN.matcher(safeProposed);
        while (metricMatcher.find()) {
            String metric = metricMatcher.group(1);
            if (!safeSource.contains(metric)) {
                CareerClaim metricClaim = CareerClaim.builder()
                        .text("Metric: " + metric)
                        .category(ClaimCategory.METRIC)
                        .userId(context.userId())
                        .build();
                TruthEvaluation eval = evaluate(metricClaim, context);
                evaluations.add(eval);
                if (eval.isUnsupported()) {
                    violations.add("Introduced unsupported metric: '" + metric + "'");
                }
            }
        }

        // 2. Check for skills mentioned in proposed text that were not in source text
        for (SkillResponse skill : context.getSkills()) {
            if (skill.name() != null) {
                String skillName = skill.name().trim();
                if (containsIgnoreCase(safeProposed, skillName) && !containsIgnoreCase(safeSource, skillName)) {
                    CareerClaim skillClaim = CareerClaim.builder()
                            .text(skillName)
                            .category(ClaimCategory.SKILL)
                            .userId(context.userId())
                            .build();
                    evaluations.add(evaluate(skillClaim, context));
                }
            }
        }

        // 3. Check for obvious fabricated technology indicators if common buzzwords appear without backing
        // (e.g. proposed text claims "Kubernetes", "AWS", "Docker" when absent from both source and context)
        List<String> commonTechChecks = List.of("Kubernetes", "Docker", "AWS", "GCP", "Azure", "Terraform", "GraphQL", "Kafka");
        for (String tech : commonTechChecks) {
            if (containsIgnoreCase(safeProposed, tech) && !containsIgnoreCase(safeSource, tech)) {
                if (context.findSkill(tech).isEmpty()) {
                    CareerClaim techClaim = CareerClaim.builder()
                            .text(tech)
                            .category(ClaimCategory.SKILL)
                            .userId(context.userId())
                            .build();
                    TruthEvaluation eval = evaluate(techClaim, context);
                    evaluations.add(eval);
                    violations.add("Introduced unsupported technology: '" + tech + "'");
                }
            }
        }

        if (!violations.isEmpty()) {
            return TextIntegrityAssessment.unsupported(
                    safeSource, safeProposed, evaluations,
                    "Integrity violation: AI proposed text introduced unsupported facts: " + String.join("; ", violations)
            );
        }

        boolean hasNeedsConfirmation = evaluations.stream().anyMatch(TruthEvaluation::requiresConfirmation);
        if (hasNeedsConfirmation) {
            return TextIntegrityAssessment.needsConfirmation(
                    safeSource, safeProposed, evaluations,
                    "Proposed text contains partially supported elements requiring user confirmation"
            );
        }

        boolean hasDerived = evaluations.stream().anyMatch(TruthEvaluation::isDerived);
        if (hasDerived) {
            return TextIntegrityAssessment.derived(
                    safeSource, safeProposed, evaluations,
                    "Proposed text represents a safe factual derivation of verified profile information"
            );
        }

        return TextIntegrityAssessment.verified(
                safeSource, safeProposed, evaluations,
                "Proposed text is completely grounded in verified profile data and source text"
        );
    }

    private void validateInputs(CareerClaim claim, CareerFactContext context) {
        if (claim == null) {
            throw new InvalidClaimException("CareerClaim must not be null");
        }
        if (claim.text() == null || claim.text().isBlank()) {
            throw new InvalidClaimException("CareerClaim text must not be null or blank");
        }
        if (claim.category() == null) {
            throw new InvalidClaimException("CareerClaim category must not be null");
        }
        if (context == null || context.userId() == null) {
            throw new InvalidCareerContextException("CareerFactContext must not be null and must possess a non-null userId");
        }
        if (claim.userId() != null && !claim.userId().equals(context.userId())) {
            throw new CrossUserContextException(
                    "User mismatch: claim asserted for user '" + claim.userId() + "' cannot be evaluated against context of user '" + context.userId() + "'"
            );
        }
    }

    private static boolean containsIgnoreCase(String source, String target) {
        if (source == null || target == null || target.isBlank()) return false;
        return source.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
    }
}
