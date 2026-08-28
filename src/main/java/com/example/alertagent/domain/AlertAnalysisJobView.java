package com.example.alertagent.domain;

import java.time.Instant;

public record AlertAnalysisJobView(
        String alertId,
        AlertAnalysisJobStatus status,
        int attempt,
        Instant leaseUntil,
        Instant nextRetryAt,
        AlertAnalysisResponse result,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
}
