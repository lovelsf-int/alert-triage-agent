from __future__ import annotations

import os
from contextlib import AbstractAsyncContextManager
from typing import Any

import httpx
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from psycopg.rows import dict_row
from psycopg_pool import AsyncConnectionPool

from .config import Settings
from .embeddings import DashScopeEmbeddingClient
from .graph import GraphDependencies, build_graph
from .llm import DeepSeekAssessmentModel
from .repository import KnowledgeRepository
from .service import AlertAnalysisService


class ApplicationRuntime:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.http_client: httpx.AsyncClient | None = None
        self.pool: AsyncConnectionPool | None = None
        self.repository: KnowledgeRepository | None = None
        self.analysis_service: AlertAnalysisService | None = None
        self._checkpointer_context: AbstractAsyncContextManager[Any] | None = None

    async def start(self) -> None:
        self.settings.validate_runtime_secrets()
        os.environ.setdefault("LANGGRAPH_STRICT_MSGPACK", "true")

        self.http_client = httpx.AsyncClient()
        embeddings = DashScopeEmbeddingClient(self.settings, self.http_client)
        self.pool = AsyncConnectionPool(
            conninfo=self.settings.database_url,
            min_size=self.settings.db_min_pool_size,
            max_size=self.settings.db_max_pool_size,
            open=False,
            kwargs={"autocommit": True, "row_factory": dict_row},
        )
        await self.pool.open()
        await self.pool.wait(timeout=30.0)

        self.repository = KnowledgeRepository(self.settings, self.pool, embeddings)
        await self.repository.initialize()
        if self.settings.rag_seed_on_startup:
            await self.repository.reindex_seed()

        self._checkpointer_context = AsyncPostgresSaver.from_conn_string(
            self.settings.database_url
        )
        checkpointer = await self._checkpointer_context.__aenter__()
        await checkpointer.setup()

        model = DeepSeekAssessmentModel(self.settings)
        graph = build_graph(
            GraphDependencies(
                settings=self.settings,
                repository=self.repository,
                model=model,
            ),
            checkpointer,
        )
        self.analysis_service = AlertAnalysisService(graph, model.engine_name)

    async def close(self) -> None:
        if self._checkpointer_context is not None:
            await self._checkpointer_context.__aexit__(None, None, None)
            self._checkpointer_context = None
        if self.pool is not None:
            await self.pool.close()
            self.pool = None
        if self.http_client is not None:
            await self.http_client.aclose()
            self.http_client = None

    def require_repository(self) -> KnowledgeRepository:
        if self.repository is None:
            raise RuntimeError("Application runtime is not started")
        return self.repository

    def require_analysis_service(self) -> AlertAnalysisService:
        if self.analysis_service is None:
            raise RuntimeError("Application runtime is not started")
        return self.analysis_service
