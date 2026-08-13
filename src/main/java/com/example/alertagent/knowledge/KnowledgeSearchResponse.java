package com.example.alertagent.knowledge;

import java.util.List;

public record KnowledgeSearchResponse(
        String query,
        KnowledgeType type,
        int topK,
        double similarityThreshold,
        List<KnowledgeSearchHit> hits
) {
    public KnowledgeSearchResponse {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
