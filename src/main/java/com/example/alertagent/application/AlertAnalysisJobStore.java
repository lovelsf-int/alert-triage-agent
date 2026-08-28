package com.example.alertagent.application;

import com.example.alertagent.domain.AlertAnalysisJob;
import com.example.alertagent.domain.AlertAnalysisResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertAnalysisJobStore {

    boolean createPending(AlertAnalysisJob job);

    Optional<AlertAnalysisJob> findById(String alertId);

    Optional<AlertAnalysisJob> tryClaim(
            String alertId,
            String runId,
            Instant now,
            Instant leaseUntil
    );

    boolean complete(
            String alertId,
            String runId,
            AlertAnalysisResponse result,
            Instant now
    );

    boolean markRetry(
            String alertId,
            String runId,
            Instant nextRetryAt,
            String error,
            Instant now
    );

    boolean markDead(
            String alertId,
            String runId,
            String error,
            Instant now
    );

    List<String> findRecoverableIds(Instant now, int limit);
}
