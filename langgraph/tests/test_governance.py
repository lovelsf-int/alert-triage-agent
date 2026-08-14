from alert_triage_langgraph.governance import enforce_governance
from alert_triage_langgraph.models import (
    AlertAnalysisRequest,
    AlertAssessment,
    AlertSeverity,
    Evidence,
    HistoricalCase,
    PolicyRule,
    Verdict,
)


def test_unknown_evidence_forces_review_and_insufficient_evidence() -> None:
    alert = AlertAnalysisRequest(
        alertId="A-1",
        alertType="AUTH",
        title="Suspicious sign-in",
        description="Failed attempts followed by success",
        severity="HIGH",
    )
    draft = AlertAssessment(
        verdict="TRUE_POSITIVE",
        confidence=0.99,
        severity="LOW",
        summary="Likely compromise",
        evidence=[
            Evidence(
                sourceType="HISTORICAL_CASE",
                sourceId="HALLUCINATED",
                fact="not present",
            )
        ],
        matchedCaseIds=["HALLUCINATED"],
        policyReferences=[],
        recommendedActions=["Review logs"],
        requiresHumanReview=False,
        rationale="The model referenced evidence.",
    )

    result, errors = enforce_governance(draft, alert, [], [], 0.85)

    assert result.verdict is Verdict.INSUFFICIENT_EVIDENCE
    assert result.severity is AlertSeverity.HIGH
    assert result.requires_human_review is True
    assert result.evidence == []
    assert errors


def test_valid_evidence_can_complete_without_review() -> None:
    alert = AlertAnalysisRequest(
        alertId="A-2",
        alertType="AUTH",
        title="Suspicious sign-in",
        description="Failed attempts followed by success",
        severity="HIGH",
    )
    cases = [
        HistoricalCase(
            sourceId="CASE-1",
            title="Known pattern",
            body="Same pattern",
            verdict="TRUE_POSITIVE",
            score=0.91,
        )
    ]
    policies = [
        PolicyRule(
            sourceId="POL-1",
            title="Auth policy",
            body="Review sessions",
            score=0.88,
        )
    ]
    draft = AlertAssessment(
        verdict="TRUE_POSITIVE",
        confidence=0.95,
        severity="HIGH",
        summary="Likely compromise",
        evidence=[
            Evidence(sourceType="ALERT", sourceId="A-2", fact="Observed sequence"),
            Evidence(
                sourceType="HISTORICAL_CASE", sourceId="CASE-1", fact="Pattern match"
            ),
            Evidence(sourceType="POLICY", sourceId="POL-1", fact="Policy applies"),
        ],
        matchedCaseIds=["CASE-1"],
        policyReferences=["POL-1"],
        recommendedActions=["Review sessions"],
        requiresHumanReview=False,
        rationale="The alert, case and policy agree.",
    )

    result, errors = enforce_governance(draft, alert, cases, policies, 0.85)

    assert result.verdict is Verdict.TRUE_POSITIVE
    assert result.requires_human_review is False
    assert errors == []
