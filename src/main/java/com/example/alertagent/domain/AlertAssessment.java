package com.example.alertagent.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertAssessment(
        Verdict verdict,
        double confidence,
        AlertSeverity severity,
        String summary,
        List<Evidence> evidence,
        List<String> matchedCaseIds,
        List<String> policyReferences,
        List<String> recommendedActions,
        boolean requiresHumanReview,
        String rationale
) {
    public AlertAssessment {
        Objects.requireNonNull(verdict, "verdict must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
        summary = requireText(summary, "summary");
        rationale = requireText(rationale, "rationale");
        evidence = immutable(evidence);
        matchedCaseIds = immutable(matchedCaseIds);
        policyReferences = immutable(policyReferences);
        recommendedActions = immutable(recommendedActions);
    }

    public AlertAssessment enforceGovernance(double humanReviewThreshold, AlertSeverity requestedSeverity) {
        AlertSeverity effectiveSeverity = AlertSeverity.max(severity, requestedSeverity);
        boolean forcedReview = requiresHumanReview
                || confidence < humanReviewThreshold
                || effectiveSeverity == AlertSeverity.CRITICAL
                || verdict == Verdict.INSUFFICIENT_EVIDENCE
                || evidence.isEmpty();

        return new AlertAssessment(
                verdict,
                confidence,
                effectiveSeverity,
                summary,
                evidence,
                matchedCaseIds,
                policyReferences,
                recommendedActions,
                forcedReview,
                rationale
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
