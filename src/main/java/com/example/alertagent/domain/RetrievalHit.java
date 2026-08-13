package com.example.alertagent.domain;

public record RetrievalHit(
        EvidenceSourceType sourceType,
        String sourceId,
        String title,
        Double score
) {
}
