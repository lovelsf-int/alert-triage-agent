package com.example.alertagent.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertAnalysisJobTest {

    @Test
    void onlyTheCurrentRunCanCompleteAJob() {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        AlertAnalysisJob pending = AlertAnalysisJob.pending(request(), now);

        AlertAnalysisJob firstRun = pending.claim(
                "run-1",
                now,
                now.plusSeconds(30)
        ).orElseThrow();

        assertThat(firstRun.status()).isEqualTo(AlertAnalysisJobStatus.RUNNING);
        assertThat(firstRun.attempt()).isEqualTo(1);
        assertThat(firstRun.complete("stale-run", response(now), now.plusSeconds(1))).isEmpty();

        AlertAnalysisJob completed = firstRun.complete(
                "run-1",
                response(now),
                now.plusSeconds(1)
        ).orElseThrow();

        assertThat(completed.status()).isEqualTo(AlertAnalysisJobStatus.SUCCEEDED);
        assertThat(completed.result()).isNotNull();
        assertThat(completed.leaseUntil()).isNull();
    }

    @Test
    void anExpiredLeaseCanBeClaimedAgainAndRejectsTheOldRun() {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");

        AlertAnalysisJob firstRun = AlertAnalysisJob.pending(request(), now)
                .claim("run-1", now, now.plusSeconds(30))
                .orElseThrow();

        assertThat(firstRun.isClaimable(now.plusSeconds(20))).isFalse();

        AlertAnalysisJob secondRun = firstRun.claim(
                "run-2",
                now.plusSeconds(31),
                now.plusSeconds(61)
        ).orElseThrow();

        assertThat(secondRun.attempt()).isEqualTo(2);
        assertThat(secondRun.complete("run-1", response(now), now.plusSeconds(32))).isEmpty();
        assertThat(secondRun.complete("run-2", response(now), now.plusSeconds(32))).isPresent();
    }

    @Test
    void retryWaitBecomesClaimableOnlyAfterItsDeadline() {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        AlertAnalysisJob running = AlertAnalysisJob.pending(request(), now)
                .claim("run-1", now, now.plusSeconds(30))
                .orElseThrow();

        AlertAnalysisJob retrying = running.retry(
                "run-1",
                now.plusSeconds(10),
                "temporary failure",
                now.plusSeconds(1)
        ).orElseThrow();

        assertThat(retrying.status()).isEqualTo(AlertAnalysisJobStatus.RETRY_WAIT);
        assertThat(retrying.isClaimable(now.plusSeconds(9))).isFalse();
        assertThat(retrying.isClaimable(now.plusSeconds(10))).isTrue();
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

    private AlertAnalysisResponse response(Instant now) {
        return new AlertAnalysisResponse(
                "EVENT-001",
                "analysis-1",
                now,
                "test-engine",
                null,
                null
        );
    }
}
