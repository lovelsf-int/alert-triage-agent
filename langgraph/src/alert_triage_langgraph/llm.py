from __future__ import annotations

from typing import Protocol

from langchain_deepseek import ChatDeepSeek

from .config import Settings
from .errors import ExternalServiceError
from .models import AlertAnalysisRequest, AlertAssessment, HistoricalCase, PolicyRule
from .prompts import build_assessment_messages


class AssessmentModel(Protocol):
    @property
    def engine_name(self) -> str: ...

    async def assess(
        self,
        alert: AlertAnalysisRequest,
        cases: list[HistoricalCase],
        policies: list[PolicyRule],
    ) -> AlertAssessment: ...


class DeepSeekAssessmentModel:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        model = ChatDeepSeek(
            model=settings.deepseek_model,
            api_key=settings.deepseek_api_key,
            base_url=settings.deepseek_api_base,
            temperature=settings.deepseek_temperature,
            max_tokens=settings.deepseek_max_tokens,
            max_retries=2,
            timeout=90.0,
            extra_body={"thinking": {"type": "disabled"}},
        )
        self._structured = model.with_structured_output(
            AlertAssessment,
            method="json_mode",
            include_raw=True,
        )

    @property
    def engine_name(self) -> str:
        return f"langgraph+langchain-deepseek:{self._settings.deepseek_model}"

    async def assess(
        self,
        alert: AlertAnalysisRequest,
        cases: list[HistoricalCase],
        policies: list[PolicyRule],
    ) -> AlertAssessment:
        messages = build_assessment_messages(alert, cases, policies)
        try:
            result = await self._structured.ainvoke(messages)
        except Exception as exc:
            raise ExternalServiceError("DeepSeek structured assessment failed") from exc

        parsed = result.get("parsed") if isinstance(result, dict) else None
        if parsed is None:
            parsing_error = result.get("parsing_error") if isinstance(result, dict) else None
            raise ExternalServiceError(
                f"DeepSeek returned an invalid structured response: {parsing_error!s}"
            )
        if isinstance(parsed, AlertAssessment):
            return parsed
        return AlertAssessment.model_validate(parsed)
