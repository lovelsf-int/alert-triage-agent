from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any, Literal, TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.types import interrupt

from .config import Settings
from .governance import enforce_governance, mark_human_review_complete
from .llm import AssessmentModel
from .models import (
    AlertAnalysisRequest,
    AlertAssessment,
    HistoricalCase,
    KnowledgeType,
    PolicyRule,
    RetrievalHit,
    RetrievalTrace,
    ReviewDecision,
    RunStatus,
)
from .repository import KnowledgeRepository


class AlertGraphState(TypedDict, total=False):
    analysis_id: str
    request: dict[str, Any]
    normalized_alert: dict[str, Any]
    retrieval_query: str
    historical_cases: list[dict[str, Any]]
    policy_rules: list[dict[str, Any]]
    draft_assessment: dict[str, Any]
    assessment: dict[str, Any]
    validation_errors: list[str]
    retrieval: dict[str, Any]
    status: str
    engine: str
    review: dict[str, Any]
    completed_at: str


@dataclass(frozen=True)
class GraphDependencies:
    settings: Settings
    repository: KnowledgeRepository
    model: AssessmentModel


def _model_dict(model: Any) -> dict[str, Any]:
    return model.model_dump(mode="json", by_alias=False)


def _utc_iso() -> str:
    return datetime.now(UTC).isoformat()


def build_graph(dependencies: GraphDependencies, checkpointer: Any) -> Any:
    settings = dependencies.settings
    repository = dependencies.repository
    model = dependencies.model

    async def normalize_alert(state: AlertGraphState) -> AlertGraphState:
        alert = AlertAnalysisRequest.model_validate(state["request"])
        attributes = json.dumps(
            alert.attributes,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        query = " ".join(
            [
                alert.alert_type,
                alert.title,
                alert.description,
                alert.severity.value,
                alert.source,
                alert.asset_id,
                attributes,
            ]
        )
        return {
            "normalized_alert": _model_dict(alert),
            "retrieval_query": query,
            "status": RunStatus.RUNNING.value,
            "engine": model.engine_name,
        }

    async def retrieve_cases(state: AlertGraphState) -> AlertGraphState:
        cases = await repository.search_historical_cases(
            state["retrieval_query"],
            settings.max_historical_cases,
        )
        return {"historical_cases": [_model_dict(item) for item in cases]}

    async def retrieve_policies(state: AlertGraphState) -> AlertGraphState:
        policies = await repository.search_policy_rules(
            state["retrieval_query"],
            settings.max_policy_rules,
        )
        return {"policy_rules": [_model_dict(item) for item in policies]}

    async def assess(state: AlertGraphState) -> AlertGraphState:
        alert = AlertAnalysisRequest.model_validate(state["normalized_alert"])
        cases = [HistoricalCase.model_validate(item) for item in state["historical_cases"]]
        policies = [PolicyRule.model_validate(item) for item in state["policy_rules"]]
        assessment = await model.assess(alert, cases, policies)
        return {"draft_assessment": _model_dict(assessment)}

    async def govern(state: AlertGraphState) -> AlertGraphState:
        alert = AlertAnalysisRequest.model_validate(state["normalized_alert"])
        cases = [HistoricalCase.model_validate(item) for item in state["historical_cases"]]
        policies = [PolicyRule.model_validate(item) for item in state["policy_rules"]]
        draft = AlertAssessment.model_validate(state["draft_assessment"])
        assessment, errors = enforce_governance(
            draft,
            alert,
            cases,
            policies,
            settings.human_review_threshold,
        )
        retrieval = RetrievalTrace(
            query=state["retrieval_query"],
            embedding_model=settings.embedding_model,
            embedding_dimensions=settings.embedding_dimensions,
            similarity_threshold=settings.rag_similarity_threshold,
            historical_cases=[
                RetrievalHit(
                    source_type=KnowledgeType.HISTORICAL_CASE,
                    source_id=item.source_id,
                    title=item.title,
                    score=item.score,
                )
                for item in cases
            ],
            policy_rules=[
                RetrievalHit(
                    source_type=KnowledgeType.POLICY_RULE,
                    source_id=item.source_id,
                    title=item.title,
                    score=item.score,
                )
                for item in policies
            ],
        )
        return {
            "assessment": _model_dict(assessment),
            "validation_errors": errors,
            "retrieval": _model_dict(retrieval),
            "status": (
                RunStatus.WAITING_FOR_REVIEW.value
                if assessment.requires_human_review
                else RunStatus.RUNNING.value
            ),
        }

    def route_after_govern(
        state: AlertGraphState,
    ) -> Literal["human_review", "finalize"]:
        assessment = AlertAssessment.model_validate(state["assessment"])
        return "human_review" if assessment.requires_human_review else "finalize"

    async def human_review(state: AlertGraphState) -> AlertGraphState:
        payload = {
            "question": "请复核该安全告警研判结果",
            "analysisId": state["analysis_id"],
            "assessment": state["assessment"],
            "retrieval": state.get("retrieval"),
            "validationErrors": state.get("validation_errors", []),
        }
        raw_decision = interrupt(payload)
        if isinstance(raw_decision, bool):
            raw_decision = {"approved": raw_decision}
        decision = ReviewDecision.model_validate(raw_decision)

        assessment = AlertAssessment.model_validate(state["assessment"])
        errors = list(state.get("validation_errors", []))
        if decision.adjusted_assessment is not None:
            alert = AlertAnalysisRequest.model_validate(state["normalized_alert"])
            cases = [HistoricalCase.model_validate(item) for item in state["historical_cases"]]
            policies = [PolicyRule.model_validate(item) for item in state["policy_rules"]]
            assessment, adjustment_errors = enforce_governance(
                decision.adjusted_assessment,
                alert,
                cases,
                policies,
                settings.human_review_threshold,
            )
            errors.extend(adjustment_errors)

        if decision.approved:
            assessment = mark_human_review_complete(assessment)
            status = RunStatus.RUNNING
        else:
            status = RunStatus.REJECTED

        return {
            "assessment": _model_dict(assessment),
            "validation_errors": list(dict.fromkeys(errors)),
            "review": _model_dict(decision),
            "status": status.value,
        }

    async def finalize(state: AlertGraphState) -> AlertGraphState:
        status = RunStatus(state.get("status", RunStatus.RUNNING.value))
        if status is not RunStatus.REJECTED:
            status = RunStatus.COMPLETED
        return {
            "status": status.value,
            "completed_at": _utc_iso(),
        }

    builder = StateGraph(AlertGraphState)
    builder.add_node("normalize_alert", normalize_alert)
    builder.add_node("retrieve_cases", retrieve_cases)
    builder.add_node("retrieve_policies", retrieve_policies)
    builder.add_node("assess", assess)
    builder.add_node("govern", govern)
    builder.add_node("human_review", human_review)
    builder.add_node("finalize", finalize)

    builder.add_edge(START, "normalize_alert")
    builder.add_edge("normalize_alert", "retrieve_cases")
    builder.add_edge("normalize_alert", "retrieve_policies")
    builder.add_edge(["retrieve_cases", "retrieve_policies"], "assess")
    builder.add_edge("assess", "govern")
    builder.add_conditional_edges(
        "govern",
        route_after_govern,
        {
            "human_review": "human_review",
            "finalize": "finalize",
        },
    )
    builder.add_edge("human_review", "finalize")
    builder.add_edge("finalize", END)
    return builder.compile(checkpointer=checkpointer)
