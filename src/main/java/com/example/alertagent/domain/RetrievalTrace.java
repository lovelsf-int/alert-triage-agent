package com.example.alertagent.domain;

import java.util.List;

public record RetrievalTrace(
        String query,
        String embeddingModel,
        int embeddingDimensions,
        String vectorStore,
        double similarityThreshold,
        List<RetrievalHit> historicalCases,
        List<RetrievalHit> policyRules
) {
    public RetrievalTrace {
        historicalCases = historicalCases == null ? List.of() : List.copyOf(historicalCases);
        policyRules = policyRules == null ? List.of() : List.copyOf(policyRules);
    }
}
