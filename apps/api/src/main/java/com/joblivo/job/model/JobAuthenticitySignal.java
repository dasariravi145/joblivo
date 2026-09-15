package com.joblivo.job.model;

import java.util.Objects;

/**
 * Immutable representation of a single deterministic authenticity warning signal.
 *
 * @param signalType  the type of authenticity signal
 * @param riskLevel   the deterministic severity of the warning signal
 * @param explanation a human-readable explanation of what the signal means
 * @param reason      the deterministic technical reason or trigger condition
 */
public record JobAuthenticitySignal(
        JobAuthenticitySignalType signalType,
        JobAuthenticityRiskLevel riskLevel,
        String explanation,
        String reason
) {
    public JobAuthenticitySignal {
        Objects.requireNonNull(signalType, "signalType must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        if (explanation == null || explanation.isBlank()) {
            explanation = signalType.getDefaultExplanation();
        }
        if (reason == null || reason.isBlank()) {
            reason = signalType.name();
        }
    }

    /**
     * Convenience factory for creating a signal with its default risk level and explanation.
     *
     * @param signalType the signal type
     * @param reason     the specific deterministic reason
     * @return a new JobAuthenticitySignal
     */
    public static JobAuthenticitySignal of(JobAuthenticitySignalType signalType, String reason) {
        Objects.requireNonNull(signalType, "signalType must not be null");
        return new JobAuthenticitySignal(
                signalType,
                signalType.getDefaultRiskLevel(),
                signalType.getDefaultExplanation(),
                reason
        );
    }

    /**
     * Convenience factory for creating a signal with an explicit risk level, explanation, and reason.
     */
    public static JobAuthenticitySignal of(JobAuthenticitySignalType signalType, JobAuthenticityRiskLevel riskLevel, String explanation, String reason) {
        return new JobAuthenticitySignal(signalType, riskLevel, explanation, reason);
    }
}
