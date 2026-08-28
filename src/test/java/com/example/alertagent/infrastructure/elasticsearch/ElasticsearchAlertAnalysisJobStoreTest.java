package com.example.alertagent.infrastructure.elasticsearch;

import com.example.alertagent.config.AsyncAnalysisProperties;
import com.example.alertagent.domain.AlertAnalysisJobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticsearchAlertAnalysisJobStoreTest {

    @Test
    void recoverySelectionDoesNotStarveExpiredOrRetryingJobs() {
        AlertAnalysisJobRepository repository = mock(AlertAnalysisJobRepository.class);
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        Instant now = Instant.parse("2026-08-29T00:00:00Z");

        when(repository.findByStatusAndLeaseUntilLessThanEqual(
                eq(AlertAnalysisJobStatus.RUNNING),
                eq(now),
                any(Pageable.class)
        )).thenReturn(List.of(document("RUNNING-1"), document("RUNNING-2")));

        when(repository.findByStatusAndNextRetryAtLessThanEqual(
                eq(AlertAnalysisJobStatus.RETRY_WAIT),
                eq(now),
                any(Pageable.class)
        )).thenReturn(List.of(document("RETRY-1"), document("RETRY-2")));

        when(repository.findByStatus(
                eq(AlertAnalysisJobStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of(document("PENDING-1"), document("PENDING-2")));

        ElasticsearchAlertAnalysisJobStore store = new ElasticsearchAlertAnalysisJobStore(
                repository,
                operations,
                new ObjectMapper(),
                new AsyncAnalysisProperties()
        );

        assertThat(store.findRecoverableIds(now, 5))
                .containsExactly(
                        "RUNNING-1",
                        "RETRY-1",
                        "PENDING-1",
                        "RUNNING-2",
                        "RETRY-2"
                );
    }

    private AlertAnalysisJobDocument document(String alertId) {
        AlertAnalysisJobDocument document = new AlertAnalysisJobDocument();
        document.setAlertId(alertId);
        return document;
    }
}
