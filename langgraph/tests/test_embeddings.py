import json

import httpx
from pydantic import SecretStr

from alert_triage_langgraph.config import Settings
from alert_triage_langgraph.embeddings import DashScopeEmbeddingClient


async def test_query_and_document_use_different_text_types() -> None:
    seen: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        seen.append(payload["parameters"]["text_type"])
        texts = payload["input"]["texts"]
        embeddings = [
            {"text_index": index, "embedding": [float(index)] * 64}
            for index, _ in enumerate(texts)
        ]
        return httpx.Response(200, json={"output": {"embeddings": embeddings}})

    settings = Settings(
        _env_file=None,
        dashscope_api_key=SecretStr("test"),
        embedding_dimensions=64,
        embedding_batch_size=10,
    )
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        embeddings = DashScopeEmbeddingClient(settings, client)
        query = await embeddings.embed_query("query")
        documents = await embeddings.embed_documents(["doc-1", "doc-2"])

    assert len(query) == 64
    assert len(documents) == 2
    assert seen == ["query", "document"]
