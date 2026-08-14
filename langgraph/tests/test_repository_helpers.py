from alert_triage_langgraph.models import KnowledgeDocumentRequest
from alert_triage_langgraph.repository import KnowledgeRepository


def test_document_id_is_deterministic_and_versioned() -> None:
    first = KnowledgeDocumentRequest(
        type="POLICY_RULE",
        sourceId="POL-1",
        title="Policy",
        content="Body",
        version="1",
    )
    same = first.model_copy()
    next_version = first.model_copy(update={"version": "2"})

    assert KnowledgeRepository.document_id(first) == KnowledgeRepository.document_id(same)
    assert KnowledgeRepository.document_id(first) != KnowledgeRepository.document_id(next_version)


def test_embedding_text_contains_retrieval_fields() -> None:
    request = KnowledgeDocumentRequest(
        type="HISTORICAL_CASE",
        sourceId="CASE-1",
        title="Account takeover",
        content="Failed logins followed by success",
        verdict="TRUE_POSITIVE",
        keywords=["login", "account"],
        recommendedActions=["Review sessions"],
    )

    text = KnowledgeRepository.embedding_text(request)

    assert "Account takeover" in text
    assert "TRUE_POSITIVE" in text
    assert "login" in text
    assert "Review sessions" in text
