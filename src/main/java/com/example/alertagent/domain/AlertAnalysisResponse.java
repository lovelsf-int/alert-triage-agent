package com.example.alertagent.domain;

import java.time.Instant;

public record AlertAnalysisResponse(
        String alertId,
        String analysisId,
        Instant createdAt,
        String engine,
        RetrievalTrace retrieval,
        AlertAssessment assessment
) {
}
