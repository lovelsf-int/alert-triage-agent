from __future__ import annotations

from collections.abc import Iterable

from .models import (
    AlertAnalysisRequest,
    AlertAssessment,
    AlertSeverity,
    Evidence,
    EvidenceSourceType,
    HistoricalCase,
    PolicyRule,
    Verdict,
)


def _deduplicate(values: Iterable[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        item = value.strip()
        if item and item not in seen:
            result.append(item)
            seen.add(item)
    return result


def enforce_governance(
    draft: AlertAssessment,
    alert: AlertAnalysisRequest,
    cases: list[HistoricalCase],
    policies: list[PolicyRule],
    human_review_threshold: float,
) -> tuple[AlertAssessment, list[str]]:
    errors: list[str] = []
    case_ids = {item.source_id for item in cases}
    policy_ids = {item.source_id for item in policies}

    valid_evidence: list[Evidence] = []
    for evidence in draft.evidence:
        valid = (
            evidence.source_type is EvidenceSourceType.ALERT
            and evidence.source_id == alert.alert_id
        ) or (
            evidence.source_type is EvidenceSourceType.HISTORICAL_CASE
            and evidence.source_id in case_ids
        ) or (
            evidence.source_type is EvidenceSourceType.POLICY
            and evidence.source_id in policy_ids
        )
        if valid:
            valid_evidence.append(evidence)
        else:
            errors.append(
                "Unknown evidence reference: "
                f"{evidence.source_type.value}:{evidence.source_id}"
            )

    matched_case_ids = []
    for case_id in _deduplicate(draft.matched_case_ids):
        if case_id in case_ids:
            matched_case_ids.append(case_id)
        else:
            errors.append(f"Unknown historical case reference: {case_id}")

    policy_references = []
    for policy_id in _deduplicate(draft.policy_references):
        if policy_id in policy_ids:
            policy_references.append(policy_id)
        else:
            errors.append(f"Unknown policy reference: {policy_id}")

    effective_severity = AlertSeverity.maximum(draft.severity, alert.severity)
    verdict = Verdict.INSUFFICIENT_EVIDENCE if errors else draft.verdict
    requires_review = (
        draft.requires_human_review
        or draft.confidence < human_review_threshold
        or effective_severity is AlertSeverity.CRITICAL
        or verdict is Verdict.INSUFFICIENT_EVIDENCE
        or not valid_evidence
        or bool(errors)
    )

    assessment = draft.model_copy(
        update={
            "verdict": verdict,
            "severity": effective_severity,
            "evidence": valid_evidence,
            "matched_case_ids": matched_case_ids,
            "policy_references": policy_references,
            "recommended_actions": _deduplicate(draft.recommended_actions),
            "requires_human_review": requires_review,
        }
    )
    return assessment, _deduplicate(errors)


def mark_human_review_complete(assessment: AlertAssessment) -> AlertAssessment:
    return assessment.model_copy(update={"requires_human_review": False})
