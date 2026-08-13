package com.example.alertagent.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertAssessmentTest {

    @Test
    void appliesReviewThreshold() {
        AlertAssessment assessment = new AlertAssessment(
                Verdict.TRUE_POSITIVE,
                0.70,
                AlertSeverity.MEDIUM,
                "Example summary",
                List.of(new Evidence(EvidenceSourceType.ALERT, "EVENT-1", "Observed fact")),
                List.of(),
                List.of(),
                List.of("Review"),
                false,
                "Example rationale"
        );

        AlertAssessment governed = assessment.enforceGovernance(0.85, AlertSeverity.MEDIUM);

        assertThat(governed.requiresHumanReview()).isTrue();
    }

    @Test
    void preservesTheHigherSeverity() {
        AlertAssessment assessment = new AlertAssessment(
                Verdict.TRUE_POSITIVE,
                0.95,
                AlertSeverity.MEDIUM,
                "Example summary",
                List.of(new Evidence(EvidenceSourceType.ALERT, "EVENT-1", "Observed fact")),
                List.of(),
                List.of(),
                List.of("Review"),
                false,
                "Example rationale"
        );

        AlertAssessment governed = assessment.enforceGovernance(0.85, AlertSeverity.HIGH);

        assertThat(governed.severity()).isEqualTo(AlertSeverity.HIGH);
    }
}
