package com.example.alertagent.knowledge;

import java.time.Instant;

public record KnowledgeIndexResult(
        String vectorDocumentId,
        KnowledgeType type,
        String sourceId,
        String version,
        Instant indexedAt
) {
}
