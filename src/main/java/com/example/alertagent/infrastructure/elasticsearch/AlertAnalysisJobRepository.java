package com.example.alertagent.infrastructure.elasticsearch;

import com.example.alertagent.domain.AlertAnalysisJobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.time.Instant;
import java.util.List;

public interface AlertAnalysisJobRepository
        extends ElasticsearchRepository<AlertAnalysisJobDocument, String> {

    List<AlertAnalysisJobDocument> findByStatus(
            AlertAnalysisJobStatus status,
            Pageable pageable
    );

    List<AlertAnalysisJobDocument> findByStatusAndNextRetryAtLessThanEqual(
            AlertAnalysisJobStatus status,
            Instant nextRetryAt,
            Pageable pageable
    );

    List<AlertAnalysisJobDocument> findByStatusAndLeaseUntilLessThanEqual(
            AlertAnalysisJobStatus status,
            Instant leaseUntil,
            Pageable pageable
    );
}
