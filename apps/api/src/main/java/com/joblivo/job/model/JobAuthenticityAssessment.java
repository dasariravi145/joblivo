package com.joblivo.job.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable canonical read model representing the deterministic authenticity assessment of a job.
 * <p>
 * This model captures available warning signals and aggregate outcome without computing
 * fraudulent or arbitrary probabilistic percentages.
 *
 * @param overallAssessment   the deterministic aggregate outcome (e.g. NO_WARNING, INFORMATIONAL, CAUTION, HIGH_RISK_SIGNALS, INSUFFICIENT_EVIDENCE)
 * @param highestRisk         the highest risk level found among all evaluated signals (NONE, LOW, MEDIUM, HIGH)
 * @param signals             immutable list of detected authenticity warning signals
 * @param insufficientEvidence whether available data is insufficient to establish a meaningful authenticity evaluation
 */
public record JobAuthenticityAssessment(
        JobAuthenticityOutcome overallAssessment,
        JobAuthenticityRiskLevel highestRisk,
        List<JobAuthenticitySignal> signals,
        boolean insufficientEvidence
) {
    public JobAuthenticityAssessment {
        Objects.requireNonNull(overallAssessment, "overallAssessment must not be null");
        Objects.requireNonNull(highestRisk, "highestRisk must not be null");
        signals = signals != null ? List.copyOf(signals) : Collections.emptyList();
    }

    /**
     * Factory method representing a clean job with no warning signals.
     */
    public static JobAuthenticityAssessment noWarning() {
        return new JobAuthenticityAssessment(
                JobAuthenticityOutcome.NO_WARNING,
                JobAuthenticityRiskLevel.NONE,
                Collections.emptyList(),
                false
        );
    }

    /**
     * Factory method for creating an assessment from evaluated signals and evidence sufficiency flag.
     *
     * @param signals              the detected warning signals
     * @param insufficientEvidence true if required core data is missing preventing complete evaluation
     * @return a deterministic JobAuthenticityAssessment
     */
    public static JobAuthenticityAssessment fromSignals(List<JobAuthenticitySignal> signals, boolean insufficientEvidence) {
        List<JobAuthenticitySignal> safeSignals = signals != null ? List.copyOf(signals) : Collections.emptyList();

        JobAuthenticityRiskLevel highest = JobAuthenticityRiskLevel.NONE;
        for (JobAuthenticitySignal signal : safeSignals) {
            if (signal != null && signal.riskLevel() != null) {
                highest = highest.max(signal.riskLevel());
            }
        }

        JobAuthenticityOutcome outcome;
        if (insufficientEvidence) {
            outcome = JobAuthenticityOutcome.INSUFFICIENT_EVIDENCE;
        } else if (safeSignals.isEmpty()) {
            outcome = JobAuthenticityOutcome.NO_WARNING;
        } else if (highest == JobAuthenticityRiskLevel.HIGH) {
            outcome = JobAuthenticityOutcome.HIGH_RISK_SIGNALS;
        } else if (highest == JobAuthenticityRiskLevel.MEDIUM) {
            outcome = JobAuthenticityOutcome.CAUTION;
        } else if (highest == JobAuthenticityRiskLevel.LOW) {
            outcome = JobAuthenticityOutcome.INFORMATIONAL;
        } else {
            outcome = JobAuthenticityOutcome.NO_WARNING;
        }

        return new JobAuthenticityAssessment(outcome, highest, safeSignals, insufficientEvidence);
    }
}
