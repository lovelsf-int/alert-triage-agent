package com.example.alertagent.application;

import com.example.alertagent.domain.AlertAnalysisJob;
import com.example.alertagent.domain.AlertAnalysisJobStatus;
import com.example.alertagent.domain.AlertAnalysisRequest;
import com.example.alertagent.domain.AlertAnalysisSubmissionResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncAlertAnalysisServiceTest {

    @Test
    void persistsBeforeDispatchingAndReturnsAcceptedJobMetadata() {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        AlertAnalysisJobStore store = mock(AlertAnalysisJobStore.class);
        AlertAnalysisTaskDispatcher dispatcher = mock(AlertAnalysisTaskDispatcher.class);
        AlertAnalysisRequest request = request();
        AlertAnalysisJob pending = AlertAnalysisJob.pending(request, now);

        when(store.createPending(pending)).thenReturn(true);
        when(store.findById(request.alertId())).thenReturn(Optional.of(pending));
        when(dispatcher.dispatch(request.alertId())).thenReturn(true);

        AsyncAlertAnalysisService service = new AsyncAlertAnalysisService(
                store,
                dispatcher,
                clock
        );

        AlertAnalysisSubmissionResponse response = service.submit(request);

        assertThat(response.alertId()).isEqualTo("EVENT-001");
        assertThat(response.status()).isEqualTo(AlertAnalysisJobStatus.PENDING);
        assertThat(response.created()).isTrue();
        assertThat(response.dispatched()).isTrue();
        assertThat(response.statusUrl()).isEqualTo("/api/v1/alert-analyses/EVENT-001");

        verify(store).createPending(pending);
        verify(dispatcher).dispatch("EVENT-001");
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
