from __future__ import annotations

import json
import uuid
from importlib import resources
from typing import Any

from psycopg import sql
from psycopg_pool import AsyncConnectionPool

from .config import Settings
from .embeddings import DashScopeEmbeddingClient
from .models import (
    HistoricalCase,
    KnowledgeDocumentRequest,
    KnowledgeIndexResult,
    KnowledgeSearchHit,
    KnowledgeSearchResponse,
    KnowledgeType,
    PolicyRule,
    Verdict,
)


class KnowledgeRepository:
    def __init__(
        self,
        settings: Settings,
        pool: AsyncConnectionPool,
        embeddings: DashScopeEmbeddingClient,
    ) -> None:
        self._settings = settings
        self._pool = pool
        self._embeddings = embeddings

    async def initialize(self) -> None:
        table = sql.Identifier(self._settings.knowledge_table)
        index = sql.Identifier(f"{self._settings.knowledge_table}_embedding_hnsw_idx")
        lookup_index = sql.Identifier(f"{self._settings.knowledge_table}_lookup_idx")
        dimensions = sql.SQL(str(self._settings.embedding_dimensions))

        async with self._pool.connection() as connection:
            await connection.execute("CREATE EXTENSION IF NOT EXISTS vector")
            await connection.execute(
                sql.SQL(
                    """
                    CREATE TABLE IF NOT EXISTS {} (
                        id uuid PRIMARY KEY,
                        knowledge_type text NOT NULL,
                        source_id text NOT NULL,
                        title text NOT NULL,
                        body text NOT NULL,
                        version text NOT NULL,
                        active boolean NOT NULL DEFAULT true,
                        verdict text,
                        keywords jsonb NOT NULL DEFAULT '[]'::jsonb,
                        recommended_actions jsonb NOT NULL DEFAULT '[]'::jsonb,
                        embedding vector({}) NOT NULL,
                        created_at timestamptz NOT NULL DEFAULT now(),
                        updated_at timestamptz NOT NULL DEFAULT now()
                    )
                    """
                ).format(table, dimensions)
            )
            await connection.execute(
                sql.SQL(
                    "CREATE INDEX IF NOT EXISTS {} ON {} "
                    "USING hnsw (embedding vector_cosine_ops)"
                ).format(index, table)
            )
            await connection.execute(
                sql.SQL(
                    "CREATE INDEX IF NOT EXISTS {} ON {} "
                    "(knowledge_type, active, source_id, version)"
                ).format(lookup_index, table)
            )

    async def upsert(self, request: KnowledgeDocumentRequest) -> KnowledgeIndexResult:
        return await self.upsert_many([request])

    async def upsert_many(
        self, requests: list[KnowledgeDocumentRequest]
    ) -> KnowledgeIndexResult:
        if not requests:
            return KnowledgeIndexResult(indexed=0, document_ids=[])

        texts = [self.embedding_text(request) for request in requests]
        vectors = await self._embeddings.embed_documents(texts)
        document_ids = [self.document_id(request) for request in requests]
        table = sql.Identifier(self._settings.knowledge_table)
        statement = sql.SQL(
            """
            INSERT INTO {} (
                id, knowledge_type, source_id, title, body, version, active,
                verdict, keywords, recommended_actions, embedding, updated_at
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb, %s::jsonb,
                %s::vector, now()
            )
            ON CONFLICT (id) DO UPDATE SET
                knowledge_type = EXCLUDED.knowledge_type,
                source_id = EXCLUDED.source_id,
                title = EXCLUDED.title,
                body = EXCLUDED.body,
                version = EXCLUDED.version,
                active = EXCLUDED.active,
                verdict = EXCLUDED.verdict,
                keywords = EXCLUDED.keywords,
                recommended_actions = EXCLUDED.recommended_actions,
                embedding = EXCLUDED.embedding,
                updated_at = now()
            """
        ).format(table)

        async with self._pool.connection() as connection:
            async with connection.transaction():
                for request, vector, document_id in zip(
                    requests, vectors, document_ids, strict=True
                ):
                    await connection.execute(
                        statement,
                        (
                            document_id,
                            request.type.value,
                            request.source_id,
                            request.title,
                            request.content,
                            request.version,
                            request.active,
                            request.verdict.value if request.verdict else None,
                            json.dumps(request.keywords, ensure_ascii=False),
                            json.dumps(request.recommended_actions, ensure_ascii=False),
                            self.vector_literal(vector),
                        ),
                    )

        return KnowledgeIndexResult(
            indexed=len(document_ids),
            document_ids=[str(item) for item in document_ids],
        )

    async def search(
        self,
        query: str,
        knowledge_type: KnowledgeType,
        top_k: int,
        similarity_threshold: float | None = None,
    ) -> KnowledgeSearchResponse:
        threshold = (
            self._settings.rag_similarity_threshold
            if similarity_threshold is None
            else similarity_threshold
        )
        vector = await self._embeddings.embed_query(query)
        table = sql.Identifier(self._settings.knowledge_table)
        statement = sql.SQL(
            """
            WITH query_vector AS (SELECT %s::vector AS embedding)
            SELECT
                k.id::text AS document_id,
                k.knowledge_type,
                k.source_id,
                k.title,
                k.body,
                k.version,
                k.active,
                k.verdict,
                k.keywords,
                k.recommended_actions,
                (1 - (k.embedding <=> q.embedding))::float8 AS score
            FROM {} AS k
            CROSS JOIN query_vector AS q
            WHERE k.knowledge_type = %s
              AND k.active = true
              AND (1 - (k.embedding <=> q.embedding)) >= %s
            ORDER BY k.embedding <=> q.embedding
            LIMIT %s
            """
        ).format(table)

        async with self._pool.connection() as connection:
            cursor = await connection.execute(
                statement,
                (
                    self.vector_literal(vector),
                    knowledge_type.value,
                    threshold,
                    top_k,
                ),
            )
            rows = await cursor.fetchall()

        hits = [self._row_to_search_hit(row) for row in rows]
        return KnowledgeSearchResponse(
            query=query,
            type=knowledge_type,
            embedding_model=self._settings.embedding_model,
            embedding_dimensions=self._settings.embedding_dimensions,
            similarity_threshold=threshold,
            hits=hits,
        )

    async def search_historical_cases(self, query: str, top_k: int) -> list[HistoricalCase]:
        response = await self.search(query, KnowledgeType.HISTORICAL_CASE, top_k)
        return [
            HistoricalCase(
                source_id=hit.source_id,
                title=hit.title,
                body=hit.content,
                verdict=hit.verdict or Verdict.INSUFFICIENT_EVIDENCE,
                keywords=hit.keywords,
                score=hit.score,
            )
            for hit in response.hits
        ]

    async def search_policy_rules(self, query: str, top_k: int) -> list[PolicyRule]:
        response = await self.search(query, KnowledgeType.POLICY_RULE, top_k)
        return [
            PolicyRule(
                source_id=hit.source_id,
                title=hit.title,
                body=hit.content,
                recommended_actions=hit.recommended_actions,
                keywords=hit.keywords,
                score=hit.score,
            )
            for hit in response.hits
        ]

    async def delete(
        self,
        knowledge_type: KnowledgeType,
        source_id: str,
        version: str,
    ) -> int:
        table = sql.Identifier(self._settings.knowledge_table)
        statement = sql.SQL(
            "DELETE FROM {} WHERE knowledge_type = %s AND source_id = %s AND version = %s"
        ).format(table)
        async with self._pool.connection() as connection:
            cursor = await connection.execute(
                statement,
                (knowledge_type.value, source_id, version),
            )
            return cursor.rowcount

    async def reindex_seed(self) -> KnowledgeIndexResult:
        return await self.upsert_many(self.load_seed())

    @staticmethod
    def load_seed() -> list[KnowledgeDocumentRequest]:
        resource = resources.files("alert_triage_langgraph.resources").joinpath("seed.json")
        data = json.loads(resource.read_text(encoding="utf-8"))
        return [KnowledgeDocumentRequest.model_validate(item) for item in data]

    @staticmethod
    def document_id(request: KnowledgeDocumentRequest) -> uuid.UUID:
        value = f"{request.type.value}:{request.source_id}:{request.version}"
        return uuid.uuid5(uuid.NAMESPACE_URL, value)

    @staticmethod
    def embedding_text(request: KnowledgeDocumentRequest) -> str:
        parts = [
            f"知识类型：{request.type.value}",
            f"标题：{request.title}",
            f"正文：{request.content}",
        ]
        if request.keywords:
            parts.append("关键词：" + "、".join(request.keywords))
        if request.verdict:
            parts.append("历史结论：" + request.verdict.value)
        if request.recommended_actions:
            parts.append("建议动作：" + "；".join(request.recommended_actions))
        return "\n".join(parts)

    @staticmethod
    def vector_literal(vector: list[float]) -> str:
        return "[" + ",".join(format(value, ".9g") for value in vector) + "]"

    @staticmethod
    def _json_list(value: Any) -> list[str]:
        if value is None:
            return []
        if isinstance(value, str):
            value = json.loads(value)
        if not isinstance(value, list):
            return []
        return [str(item) for item in value]

    def _row_to_search_hit(self, row: dict[str, Any]) -> KnowledgeSearchHit:
        verdict = row.get("verdict")
        return KnowledgeSearchHit(
            document_id=row["document_id"],
            type=KnowledgeType(row["knowledge_type"]),
            source_id=row["source_id"],
            title=row["title"],
            content=row["body"],
            version=row["version"],
            active=bool(row["active"]),
            score=float(row["score"]),
            verdict=Verdict(verdict) if verdict else None,
            keywords=self._json_list(row.get("keywords")),
            recommended_actions=self._json_list(row.get("recommended_actions")),
        )
