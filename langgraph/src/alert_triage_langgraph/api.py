from __future__ import annotations

from fastapi import APIRouter, Query, Request, Response, status

from .models import (
    AlertAnalysisRequest,
    AnalysisRunResponse,
    DeleteKnowledgeResponse,
    KnowledgeDocumentRequest,
    KnowledgeIndexResult,
    KnowledgeSearchResponse,
    KnowledgeType,
    ReviewDecision,
    RunStatus,
)
from .runtime import ApplicationRuntime

router = APIRouter(prefix="/api/v1")


def _runtime(request: Request) -> ApplicationRuntime:
    return request.app.state.runtime


@router.post("/alert-analyses", response_model=AnalysisRunResponse)
async def analyze_alert(
    payload: AlertAnalysisRequest,
    request: Request,
    response: Response,
) -> AnalysisRunResponse:
    result = await _runtime(request).require_analysis_service().analyze(payload)
    if result.status is RunStatus.WAITING_FOR_REVIEW:
        response.status_code = status.HTTP_202_ACCEPTED
    return result


@router.get("/alert-analyses/{analysis_id}", response_model=AnalysisRunResponse)
async def get_analysis(analysis_id: str, request: Request) -> AnalysisRunResponse:
    return await _runtime(request).require_analysis_service().get(analysis_id)


@router.post(
    "/alert-analyses/{analysis_id}/reviews",
    response_model=AnalysisRunResponse,
)
async def review_analysis(
    analysis_id: str,
    payload: ReviewDecision,
    request: Request,
) -> AnalysisRunResponse:
    return await _runtime(request).require_analysis_service().review(analysis_id, payload)


@router.post("/knowledge", response_model=KnowledgeIndexResult)
async def upsert_knowledge(
    payload: KnowledgeDocumentRequest,
    request: Request,
) -> KnowledgeIndexResult:
    return await _runtime(request).require_repository().upsert(payload)


@router.post("/knowledge/batch", response_model=KnowledgeIndexResult)
async def upsert_knowledge_batch(
    payload: list[KnowledgeDocumentRequest],
    request: Request,
) -> KnowledgeIndexResult:
    return await _runtime(request).require_repository().upsert_many(payload)


@router.get("/knowledge/search", response_model=KnowledgeSearchResponse)
async def search_knowledge(
    request: Request,
    query: str = Query(min_length=1, max_length=12000),
    type: KnowledgeType = Query(),
    top_k: int = Query(default=5, alias="topK", ge=1, le=50),
    similarity_threshold: float | None = Query(
        default=None,
        alias="similarityThreshold",
        ge=-1.0,
        le=1.0,
    ),
) -> KnowledgeSearchResponse:
    return await _runtime(request).require_repository().search(
        query,
        type,
        top_k,
        similarity_threshold,
    )


@router.post("/knowledge/reindex-seed", response_model=KnowledgeIndexResult)
async def reindex_seed(request: Request) -> KnowledgeIndexResult:
    return await _runtime(request).require_repository().reindex_seed()


@router.delete(
    "/knowledge/{knowledge_type}/{source_id}",
    response_model=DeleteKnowledgeResponse,
)
async def delete_knowledge(
    knowledge_type: KnowledgeType,
    source_id: str,
    request: Request,
    version: str = Query(default="1", min_length=1, max_length=64),
) -> DeleteKnowledgeResponse:
    deleted = await _runtime(request).require_repository().delete(
        knowledge_type,
        source_id,
        version,
    )
    return DeleteKnowledgeResponse(deleted=deleted)
