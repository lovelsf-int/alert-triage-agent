from typing import Any

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from alert_triage_langgraph.config import Settings
from alert_triage_langgraph.graph import GraphDependencies, build_graph
from alert_triage_langgraph.models import (
    AlertAssessment,
    Evidence,
    HistoricalCase,
    PolicyRule,
    RunStatus,
)


class FakeRepository:
    async def search_historical_cases(self, _: str, __: int) -> list[HistoricalCase]:
        return [
            HistoricalCase(
                sourceId="CASE-1",
                title="Known account takeover",
                body="Failures followed by success",
                verdict="TRUE_POSITIVE",
                keywords=["login"],
                score=0.92,
            )
        ]

    async def search_policy_rules(self, _: str, __: int) -> list[PolicyRule]:
        return [
            PolicyRule(
                sourceId="POL-1",
                title="Account takeover response",
                body="Review sessions",
                recommendedActions=["Review sessions"],
                keywords=["login"],
                score=0.89,
            )
        ]


class FakeModel:
    def __init__(self, confidence: float) -> None:
        self.confidence = confidence

    @property
    def engine_name(self) -> str:
        return "fake-model"

    async def assess(self, alert: Any, cases: Any, policies: Any) -> AlertAssessment:
        del cases, policies
        return AlertAssessment(
            verdict="TRUE_POSITIVE",
            confidence=self.confidence,
            severity="HIGH",
            summary="Likely account takeover",
            evidence=[
                Evidence(sourceType="ALERT", sourceId=alert.alert_id, fact="Observed sequence"),
                Evidence(
                    sourceType="HISTORICAL_CASE",
                    sourceId="CASE-1",
                    fact="Similar historical pattern",
                ),
                Evidence(sourceType="POLICY", sourceId="POL-1", fact="Policy applies"),
            ],
            matchedCaseIds=["CASE-1"],
            policyReferences=["POL-1"],
            recommendedActions=["Review sessions"],
            requiresHumanReview=False,
            rationale="Evidence agrees.",
        )


def initial_input(analysis_id: str) -> dict[str, Any]:
    return {
        "analysis_id": analysis_id,
        "request": {
            "alert_id": "A-1",
            "alert_type": "AUTH",
            "title": "Failed logins followed by success",
            "description": "Multiple sources then a successful login",
            "severity": "HIGH",
            "source": "iam",
            "asset_id": "user:demo",
            "attributes": {"failedAttempts": 12},
        },
        "status": "RUNNING",
        "engine": "fake-model",
    }


async def test_graph_completes_when_governance_allows_automatic_result() -> None:
    graph = build_graph(
        GraphDependencies(
            settings=Settings(_env_file=None, human_review_threshold=0.85),
            repository=FakeRepository(),
            model=FakeModel(confidence=0.95),
        ),
        InMemorySaver(),
    )
    result = await graph.ainvoke(
        initial_input("run-complete"),
        config={"configurable": {"thread_id": "run-complete"}},
    )

    assert result["status"] == RunStatus.COMPLETED.value
    assert result["assessment"]["requires_human_review"] is False
    assert len(result["historical_cases"]) == 1
    assert len(result["policy_rules"]) == 1


async def test_graph_interrupts_and_resumes_human_review() -> None:
    graph = build_graph(
        GraphDependencies(
            settings=Settings(_env_file=None, human_review_threshold=0.85),
            repository=FakeRepository(),
            model=FakeModel(confidence=0.40),
        ),
        InMemorySaver(),
    )
    config = {"configurable": {"thread_id": "run-review"}}
    interrupted = await graph.ainvoke(initial_input("run-review"), config=config)

    assert interrupted["status"] == RunStatus.WAITING_FOR_REVIEW.value
    assert interrupted["__interrupt__"]

    resumed = await graph.ainvoke(
        Command(resume={"approved": True, "comment": "Reviewed"}),
        config=config,
    )

    assert resumed["status"] == RunStatus.COMPLETED.value
    assert resumed["assessment"]["requires_human_review"] is False
    assert resumed["review"]["approved"] is True
