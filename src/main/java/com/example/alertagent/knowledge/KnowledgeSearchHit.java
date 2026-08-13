package com.example.alertagent.knowledge;

import java.util.Map;

public record KnowledgeSearchHit(
        String vectorDocumentId,
        KnowledgeType type,
        String sourceId,
        String title,
        String content,
        String version,
        boolean active,
        Double score,
        Map<String, Object> metadata
) {
    public KnowledgeSearchHit {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
