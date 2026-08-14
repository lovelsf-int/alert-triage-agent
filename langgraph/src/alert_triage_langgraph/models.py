from __future__ import annotations

from datetime import UTC, datetime
from enum import Enum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


def to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


def utc_now() -> datetime:
    return datetime.now(UTC)


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class AlertSeverity(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"

    @property
    def rank(self) -> int:
        return {
            AlertSeverity.LOW: 0,
            AlertSeverity.MEDIUM: 1,
            AlertSeverity.HIGH: 2,
            AlertSeverity.CRITICAL: 3,
        }[self]

    @classmethod
    def maximum(cls, left: AlertSeverity, right: AlertSeverity) -> AlertSeverity:
        return left if left.rank >= right.rank else right


class Verdict(str, Enum):
    TRUE_POSITIVE = "TRUE_POSITIVE"
    FALSE_POSITIVE = "FALSE_POSITIVE"
    INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE"


class EvidenceSourceType(str, Enum):
    ALERT = "ALERT"
    HISTORICAL_CASE = "HISTORICAL_CASE"
    POLICY = "POLICY"


class KnowledgeType(str, Enum):
    HISTORICAL_CASE = "HISTORICAL_CASE"
    POLICY_RULE = "POLICY_RULE"


class RunStatus(str, Enum):
    RUNNING = "RUNNING"
    WAITING_FOR_REVIEW = "WAITING_FOR_REVIEW"
    COMPLETED = "COMPLETED"
    REJECTED = "REJECTED"


class AlertAnalysisRequest(ApiModel):
    alert_id: str = Field(min_length=1, max_length=128)
    alert_type: str = Field(min_length=1, max_length=128)
    title: str = Field(min_length=1, max_length=512)
    description: str = Field(min_length=1, max_length=12000)
    severity: AlertSeverity = AlertSeverity.MEDIUM
    source: str = Field(default="unknown", max_length=128)
    asset_id: str = Field(default="unknown", max_length=256)
    occurred_at: datetime = Field(default_factory=utc_now)
    attributes: dict[str, Any] = Field(default_factory=dict)

    @field_validator("alert_id", "alert_type", "title", "description")
    @classmethod
    def reject_blank_required_text(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value.strip()

    @field_validator("source", "asset_id", mode="before")
    @classmethod
    def default_optional_text(cls, value: Any) -> str:
        if value is None or not str(value).strip():
            return "unknown"
        return str(value).strip()


class Evidence(ApiModel):
    source_type: EvidenceSourceType
    source_id: str = Field(min_length=1, max_length=256)
    fact: str = Field(min_length=1, max_length=2000)


class AlertAssessment(ApiModel):
    verdict: Verdict
    confidence: float = Field(ge=0.0, le=1.0)
    severity: AlertSeverity
    summary: str = Field(min_length=1, max_length=4000)
    evidence: list[Evidence] = Field(default_factory=list)
    matched_case_ids: list[str] = Field(default_factory=list)
    policy_references: list[str] = Field(default_factory=list)
    recommended_actions: list[str] = Field(default_factory=list)
    requires_human_review: bool = False
    rationale: str = Field(min_length=1, max_length=8000)


class HistoricalCase(ApiModel):
    source_id: str
    title: str
    body: str
    verdict: Verdict
    keywords: list[str] = Field(default_factory=list)
    score: float


class PolicyRule(ApiModel):
    source_id: str
    title: str
    body: str
    recommended_actions: list[str] = Field(default_factory=list)
    keywords: list[str] = Field(default_factory=list)
    score: float


class RetrievalHit(ApiModel):
    source_type: KnowledgeType
    source_id: str
    title: str
    score: float


class RetrievalTrace(ApiModel):
    query: str
    embedding_model: str
    embedding_dimensions: int
    vector_store: str = "pgvector"
    similarity_threshold: float
    historical_cases: list[RetrievalHit] = Field(default_factory=list)
    policy_rules: list[RetrievalHit] = Field(default_factory=list)


class ReviewDecision(ApiModel):
    approved: bool
    comment: str = Field(default="", max_length=4000)
    adjusted_assessment: AlertAssessment | None = None


class AnalysisRunResponse(ApiModel):
    alert_id: str
    analysis_id: str
    status: RunStatus
    engine: str
    retrieval: RetrievalTrace | None = None
    assessment: AlertAssessment | None = None
    validation_errors: list[str] = Field(default_factory=list)
    interrupt: dict[str, Any] | None = None
    review: ReviewDecision | None = None
    completed_at: datetime | None = None


class KnowledgeDocumentRequest(ApiModel):
    type: KnowledgeType
    source_id: str = Field(min_length=1, max_length=256)
    title: str = Field(min_length=1, max_length=1000)
    content: str = Field(min_length=1, max_length=50000)
    verdict: Verdict | None = None
    keywords: list[str] = Field(default_factory=list)
    recommended_actions: list[str] = Field(default_factory=list)
    version: str = Field(default="1", min_length=1, max_length=64)
    active: bool = True

    @field_validator("source_id", "title", "content", "version")
    @classmethod
    def reject_blank_knowledge_text(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value.strip()

    @field_validator("keywords", "recommended_actions")
    @classmethod
    def normalize_string_lists(cls, values: list[str]) -> list[str]:
        normalized: list[str] = []
        seen: set[str] = set()
        for value in values:
            item = value.strip()
            if item and item not in seen:
                normalized.append(item)
                seen.add(item)
        return normalized


class KnowledgeIndexResult(ApiModel):
    indexed: int
    document_ids: list[str]


class KnowledgeSearchHit(ApiModel):
    document_id: str
    type: KnowledgeType
    source_id: str
    title: str
    content: str
    version: str
    active: bool
    score: float
    verdict: Verdict | None = None
    keywords: list[str] = Field(default_factory=list)
    recommended_actions: list[str] = Field(default_factory=list)


class KnowledgeSearchResponse(ApiModel):
    query: str
    type: KnowledgeType
    embedding_model: str
    embedding_dimensions: int
    similarity_threshold: float
    hits: list[KnowledgeSearchHit]


class DeleteKnowledgeResponse(ApiModel):
    deleted: int
