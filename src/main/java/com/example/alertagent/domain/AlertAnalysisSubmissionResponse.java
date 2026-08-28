package com.example.alertagent.domain;

import java.time.Instant;

public record AlertAnalysisSubmissionResponse(
        String alertId,
        AlertAnalysisJobStatus status,
        boolean created,
        boolean dispatched,
        Instant submittedAt,
        String statusUrl
) {
}
