package com.example.alertagent.application;

import com.example.alertagent.config.AgentProperties;
import com.example.alertagent.domain.AlertAnalysisRequest;
import com.example.alertagent.domain.AlertSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertContextEnricherTest {

    @Test
    void buildsAStableRetrievalQuery() {
        AlertContextEnricher enricher = new AlertContextEnricher(
                (query, limit) -> List.of(),
                (query, limit) -> List.of(),
                new AgentProperties(),
                new ObjectMapper()
        );

        AlertAnalysisRequest request = new AlertAnalysisRequest(
                "EVENT-1",
                "TEST_EVENT",
                "Example event",
                "Example description",
                AlertSeverity.MEDIUM,
                "example-source",
                "asset:demo",
                Instant.parse("2026-08-13T05:30:00Z"),
                Map.of("count", 3)
        );

        var context = enricher.enrich(request);

        assertThat(context.retrievalQuery())
                .contains("TEST_EVENT", "Example event", "asset:demo", "count");
        assertThat(context.historicalCases()).isEmpty();
        assertThat(context.policyRules()).isEmpty();
    }
}
