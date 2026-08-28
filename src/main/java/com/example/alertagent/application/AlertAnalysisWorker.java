package com.example.alertagent.application;

import com.example.alertagent.config.AsyncAnalysisProperties;
import com.example.alertagent.domain.AlertAnalysisJob;
import com.example.alertagent.domain.AlertAnalysisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class AlertAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(AlertAnalysisWorker.class);

    private final AlertAnalysisJobStore jobStore;
    private final AlertAnalysisService alertAnalysisService;
    private final AsyncAnalysisProperties properties;
    private final Clock clock;

    public AlertAnalysisWorker(
            AlertAnalysisJobStore jobStore,
            AlertAnalysisService alertAnalysisService,
            AsyncAnalysisProperties properties,
            @Qualifier("alertAnalysisClock") Clock clock
    ) {
        this.jobStore = jobStore;
        this.alertAnalysisService = alertAnalysisService;
        this.properties = properties;
        this.clock = clock;
    }

    public void process(String alertId) {
        String runId = UUID.randomUUID().toString();
        Instant claimTime = clock.instant();
        Instant leaseUntil = claimTime.plus(properties.getLease());

        Optional<AlertAnalysisJob> claimed = jobStore.tryClaim(
                alertId,
                runId,
                claimTime,
                leaseUntil
        );

        if (claimed.isEmpty()) {
            return;
        }

        AlertAnalysisJob job = claimed.get();
        AlertAnalysisResponse result;

        try {
            result = alertAnalysisService.analyze(job.request());
        } catch (RuntimeException exception) {
            handleFailure(job, runId, exception);
            return;
        }

        try {
            boolean completed = jobStore.complete(
                    alertId,
                    runId,
                    result,
                    clock.instant()
            );

            if (!completed) {
                log.warn(
                        "Discarded stale AI result because the run no longer owns the job: alertId={}, runId={}",
                        alertId,
                        runId
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "AI analysis succeeded but Elasticsearch completion failed; lease recovery will retry: alertId={}, runId={}",
                    alertId,
                    runId,
                    exception
            );
        }
    }

    private void handleFailure(AlertAnalysisJob job, String runId, RuntimeException exception) {
        boolean interrupted = Thread.currentThread().isInterrupted() || hasInterruptedCause(exception);
        String error = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();

        try {
            if (job.attempt() >= properties.getMaxAttempts()) {
                jobStore.markDead(
                        job.alertId(),
                        runId,
                        "Maximum attempts reached. Last error: " + error,
                        clock.instant()
                );
            } else {
                Instant nextRetryAt = clock.instant().plusMillis(calculateBackoffMillis(job.attempt()));
                jobStore.markRetry(
                        job.alertId(),
                        runId,
                        nextRetryAt,
                        error,
                        clock.instant()
                );
            }
        } catch (RuntimeException persistenceException) {
            log.error(
                    "Failed to persist analysis failure; the RUNNING lease will be recovered: alertId={}, runId={}",
                    job.alertId(),
                    runId,
                    persistenceException
            );
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private long calculateBackoffMillis(int attempt) {
        Duration base = properties.getBaseRetryDelay();
        Duration maximum = properties.getMaxRetryDelay();
        int exponent = Math.max(0, Math.min(attempt - 1, 20));

        double exponential = base.toMillis() * Math.pow(2, exponent);
        long bounded = Math.min(maximum.toMillis(), (long) exponential);
        long remaining = Math.max(0L, maximum.toMillis() - bounded);
        long jitterBound = Math.min(1000L, remaining);

        long jitter = jitterBound == 0
                ? 0
                : ThreadLocalRandom.current().nextLong(jitterBound + 1);

        return bounded + jitter;
    }

    private boolean hasInterruptedCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
