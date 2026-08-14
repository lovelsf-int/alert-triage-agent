from __future__ import annotations

import uuid
from collections.abc import Mapping
from typing import Any

from langgraph.types import Command

from .errors import AnalysisNotFoundError, InvalidReviewStateError
from .models import (
    AlertAnalysisRequest,
    AlertAssessment,
    AnalysisRunResponse,
    RetrievalTrace,
    ReviewDecision,
    RunStatus,
)


class AlertAnalysisService:
    def __init__(self, graph: Any, engine_name: str) -> None:
        self._graph = graph
        self._engine_name = engine_name

    async def analyze(self, request: AlertAnalysisRequest) -> AnalysisRunResponse:
        analysis_id = str(uuid.uuid4())
        config = self._config(analysis_id)
        result = await self._graph.ainvoke(
            {
                "analysis_id": analysis_id,
                "request": request.model_dump(mode="json", by_alias=False),
                "status": RunStatus.RUNNING.value,
                "engine": self._engine_name,
            },
            config=config,
        )
        return self._to_response(result)

    async def review(
        self,
        analysis_id: str,
        decision: ReviewDecision,
    ) -> AnalysisRunResponse:
        snapshot = await self._graph.aget_state(self._config(analysis_id))
        if not snapshot.values:
            raise AnalysisNotFoundError(f"Analysis not found: {analysis_id}")
        if snapshot.values.get("status") != RunStatus.WAITING_FOR_REVIEW.value:
            raise InvalidReviewStateError(
                f"Analysis {analysis_id} is not waiting for human review"
            )

        result = await self._graph.ainvoke(
            Command(resume=decision.model_dump(mode="json", by_alias=False)),
            config=self._config(analysis_id),
        )
        return self._to_response(result)

    async def get(self, analysis_id: str) -> AnalysisRunResponse:
        snapshot = await self._graph.aget_state(self._config(analysis_id))
        if not snapshot.values:
            raise AnalysisNotFoundError(f"Analysis not found: {analysis_id}")
        values = dict(snapshot.values)
        interrupt_value = self._snapshot_interrupt(snapshot)
        if interrupt_value is not None:
            values["__interrupt__"] = [interrupt_value]
        return self._to_response(values)

    @staticmethod
    def _config(analysis_id: str) -> dict[str, dict[str, str]]:
        return {"configurable": {"thread_id": analysis_id}}

    @staticmethod
    def _snapshot_interrupt(snapshot: Any) -> Any | None:
        for task in getattr(snapshot, "tasks", ()):
            for item in getattr(task, "interrupts", ()):
                return getattr(item, "value", item)
        return None

    def _to_response(self, state: Mapping[str, Any]) -> AnalysisRunResponse:
        request_data = state.get("normalized_alert") or state.get("request") or {}
        request = AlertAnalysisRequest.model_validate(request_data)
        assessment = (
            AlertAssessment.model_validate(state["assessment"])
            if state.get("assessment")
            else None
        )
        retrieval = (
            RetrievalTrace.model_validate(state["retrieval"])
            if state.get("retrieval")
            else None
        )
        review = (
            ReviewDecision.model_validate(state["review"])
            if state.get("review")
            else None
        )
        interrupt_value = self._extract_interrupt(state.get("__interrupt__"))
        return AnalysisRunResponse(
            alert_id=request.alert_id,
            analysis_id=str(state["analysis_id"]),
            status=RunStatus(state.get("status", RunStatus.RUNNING.value)),
            engine=str(state.get("engine", self._engine_name)),
            retrieval=retrieval,
            assessment=assessment,
            validation_errors=list(state.get("validation_errors", [])),
            interrupt=interrupt_value,
            review=review,
            completed_at=state.get("completed_at"),
        )

    @staticmethod
    def _extract_interrupt(raw: Any) -> dict[str, Any] | None:
        if not raw:
            return None
        first = raw[0] if isinstance(raw, (list, tuple)) else raw
        value = getattr(first, "value", first)
        return value if isinstance(value, dict) else {"value": value}
