package com.example.alertagent.application;

import com.example.alertagent.config.AsyncAnalysisProperties;
import com.example.alertagent.domain.AlertAnalysisJob;
import com.example.alertagent.domain.AlertAnalysisRequest;
import com.example.alertagent.domain.AlertAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertAnalysisWorkerTest {

    @Test
    void claimsRunsAndCompletesTheAnalysis() {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        AlertAnalysisJobStore store = mock(AlertAnalysisJobStore.class);
        AlertAnalysisService analysisService = mock(AlertAnalysisService.class);
        AsyncAnalysisProperties properties = new AsyncAnalysisProperties();
        AlertAnalysisRequest request = request();
        AlertAnalysisJob pending = AlertAnalysisJob.pending(request, now);
        AlertAnalysisResponse response = new AlertAnalysisResponse(
                request.alertId(),
                "analysis-1",
                now,
                "test-engine",
                null,
                null
        );

        when(store.tryClaim(eq(request.alertId()), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    String runId = invocation.getArgument(1);
                    Instant claimTime = invocation.getArgument(2);
                    Instant leaseUntil = invocation.getArgument(3);
                    return pending.claim(runId, claimTime, leaseUntil);
                });
        when(analysisService.analyze(request)).thenReturn(response);
        when(store.complete(eq(request.alertId()), anyString(), eq(response), eq(now)))
                .thenReturn(true);

        AlertAnalysisWorker worker = new AlertAnalysisWorker(
                store,
                analysisService,
                properties,
                clock
        );

        worker.process(request.alertId());

        verify(analysisService).analyze(request);
        verify(store).complete(eq(request.alertId()), anyString(), eq(response), eq(now));
    }

    private AlertAnalysisRequest request() {
        return new AlertAnalysisRequest(
                "EVENT-001",
                "AUTH_ANOMALY",
                "Unusual sign-in",
                "Several failed attempts were followed by a successful sign-in.",
                null,
                "iam",
                "user:demo",
                Instant.parse("2026-08-29T00:00:00Z"),
                Map.of("failedAttempts", 12)
        );
    }
}
