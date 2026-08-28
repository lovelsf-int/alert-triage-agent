package com.example.alertagent.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record AlertAnalysisJob(
        String alertId,
        AlertAnalysisRequest request,
        AlertAnalysisJobStatus status,
        String runId,
        int attempt,
        Instant leaseUntil,
        Instant nextRetryAt,
        AlertAnalysisResponse result,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {

    public AlertAnalysisJob {
        Objects.requireNonNull(alertId, "alertId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
    }

    public static AlertAnalysisJob pending(AlertAnalysisRequest request, Instant now) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new AlertAnalysisJob(
                request.alertId(),
                request,
                AlertAnalysisJobStatus.PENDING,
                null,
                0,
                null,
                null,
                null,
                null,
                now,
                now
        );
    }

    public boolean isClaimable(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return switch (status) {
            case PENDING -> true;
            case RETRY_WAIT -> nextRetryAt == null || !nextRetryAt.isAfter(now);
            case RUNNING -> leaseUntil == null || !leaseUntil.isAfter(now);
            case SUCCEEDED, DEAD -> false;
        };
    }

    public boolean isOwnedBy(String expectedRunId) {
        return status == AlertAnalysisJobStatus.RUNNING
                && runId != null
                && runId.equals(expectedRunId);
    }

    public Optional<AlertAnalysisJob> claim(String newRunId, Instant now, Instant newLeaseUntil) {
        Objects.requireNonNull(newRunId, "newRunId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(newLeaseUntil, "newLeaseUntil must not be null");
        if (!isClaimable(now)) {
            return Optional.empty();
        }

        return Optional.of(new AlertAnalysisJob(
                alertId,
                request,
                AlertAnalysisJobStatus.RUNNING,
                newRunId,
                attempt + 1,
                newLeaseUntil,
                null,
                null,
                null,
                createdAt,
                now
        ));
    }

    public Optional<AlertAnalysisJob> complete(
            String expectedRunId,
            AlertAnalysisResponse analysisResult,
            Instant now
    ) {
        Objects.requireNonNull(analysisResult, "analysisResult must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!isOwnedBy(expectedRunId)) {
            return Optional.empty();
        }

        return Optional.of(new AlertAnalysisJob(
                alertId,
                request,
                AlertAnalysisJobStatus.SUCCEEDED,
                expectedRunId,
                attempt,
                null,
                null,
                analysisResult,
                null,
                createdAt,
                now
        ));
    }

    public Optional<AlertAnalysisJob> retry(
            String expectedRunId,
            Instant retryAt,
            String error,
            Instant now
    ) {
        Objects.requireNonNull(retryAt, "retryAt must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!isOwnedBy(expectedRunId)) {
            return Optional.empty();
        }

        return Optional.of(new AlertAnalysisJob(
                alertId,
                request,
                AlertAnalysisJobStatus.RETRY_WAIT,
                expectedRunId,
                attempt,
                null,
                retryAt,
                null,
                normalizeError(error),
                createdAt,
                now
        ));
    }

    public Optional<AlertAnalysisJob> dead(
            String expectedRunId,
            String error,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        if (!isOwnedBy(expectedRunId)) {
            return Optional.empty();
        }

        return Optional.of(new AlertAnalysisJob(
                alertId,
                request,
                AlertAnalysisJobStatus.DEAD,
                expectedRunId,
                attempt,
                null,
                null,
                null,
                normalizeError(error),
                createdAt,
                now
        ));
    }

    public AlertAnalysisJobView toView() {
        return new AlertAnalysisJobView(
                alertId,
                status,
                attempt,
                leaseUntil,
                nextRetryAt,
                result,
                lastError,
                createdAt,
                updatedAt
        );
    }

    private static String normalizeError(String error) {
        if (error == null || error.isBlank()) {
            return "unknown error";
        }
        String trimmed = error.trim();
        return trimmed.length() <= 2000 ? trimmed : trimmed.substring(0, 2000);
    }
}
