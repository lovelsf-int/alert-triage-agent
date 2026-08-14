from __future__ import annotations

from typing import Any, Literal

import httpx

from .config import Settings
from .errors import ExternalServiceError


class DashScopeEmbeddingClient:
    def __init__(self, settings: Settings, client: httpx.AsyncClient) -> None:
        self._settings = settings
        self._client = client

    async def embed_query(self, text: str) -> list[float]:
        vectors = await self._embed([text], text_type="query")
        return vectors[0]

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        all_vectors: list[list[float]] = []
        batch_size = self._settings.embedding_batch_size
        for start in range(0, len(texts), batch_size):
            batch = texts[start : start + batch_size]
            all_vectors.extend(await self._embed(batch, text_type="document"))
        return all_vectors

    async def _embed(
        self,
        texts: list[str],
        *,
        text_type: Literal["query", "document"],
    ) -> list[list[float]]:
        cleaned = [text.strip() for text in texts]
        if not cleaned or any(not text for text in cleaned):
            raise ValueError("embedding input must contain non-blank text")

        payload = {
            "model": self._settings.embedding_model,
            "input": {"texts": cleaned},
            "parameters": {
                "text_type": text_type,
                "dimension": self._settings.embedding_dimensions,
                "output_type": "dense",
            },
        }
        headers = {
            "Authorization": (
                "Bearer " + self._settings.dashscope_api_key.get_secret_value()
            ),
            "Content-Type": "application/json",
        }

        try:
            response = await self._client.post(
                self._settings.dashscope_embedding_url,
                headers=headers,
                json=payload,
                timeout=self._settings.embedding_timeout_seconds,
            )
            response.raise_for_status()
            body: dict[str, Any] = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            detail = ""
            if isinstance(exc, httpx.HTTPStatusError):
                detail = f" status={exc.response.status_code} body={exc.response.text[:500]}"
            raise ExternalServiceError(f"DashScope embedding request failed.{detail}") from exc

        raw_embeddings = body.get("output", {}).get("embeddings")
        if not isinstance(raw_embeddings, list):
            raise ExternalServiceError("DashScope response did not contain output.embeddings")

        ordered = sorted(raw_embeddings, key=lambda item: int(item.get("text_index", 0)))
        vectors: list[list[float]] = []
        for item in ordered:
            vector = item.get("embedding")
            if not isinstance(vector, list):
                raise ExternalServiceError("DashScope returned an invalid embedding vector")
            converted = [float(value) for value in vector]
            if len(converted) != self._settings.embedding_dimensions:
                raise ExternalServiceError(
                    "DashScope embedding dimension mismatch: "
                    f"expected {self._settings.embedding_dimensions}, got {len(converted)}"
                )
            vectors.append(converted)

        if len(vectors) != len(cleaned):
            raise ExternalServiceError(
                f"DashScope embedding count mismatch: expected {len(cleaned)}, got {len(vectors)}"
            )
        return vectors
