package com.example.alertagent.infrastructure.elasticsearch;

import com.example.alertagent.application.AlertAnalysisJobStore;
import com.example.alertagent.config.AsyncAnalysisProperties;
import com.example.alertagent.domain.AlertAnalysisJob;
import com.example.alertagent.domain.AlertAnalysisJobStatus;
import com.example.alertagent.domain.AlertAnalysisRequest;
import com.example.alertagent.domain.AlertAnalysisResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SeqNoPrimaryTerm;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Repository
public class ElasticsearchAlertAnalysisJobStore implements AlertAnalysisJobStore {

    private final AlertAnalysisJobRepository repository;
    private final ElasticsearchOperations operations;
    private final ObjectMapper objectMapper;
    private final int optimisticRetries;

    public ElasticsearchAlertAnalysisJobStore(
            AlertAnalysisJobRepository repository,
            ElasticsearchOperations operations,
            ObjectMapper objectMapper,
            AsyncAnalysisProperties properties
    ) {
        this.repository = repository;
        this.operations = operations;
        this.objectMapper = objectMapper;
        this.optimisticRetries = properties.getOptimisticRetries();
    }

    @Override
    public boolean createPending(AlertAnalysisJob job) {
        AlertAnalysisJobDocument document = toDocument(job, null);
        IndexQuery query = new IndexQueryBuilder()
                .withId(job.alertId())
                .withObject(document)
                .withOpType(IndexQuery.OpType.CREATE)
                .build();

        try {
            operations.index(
                    query,
                    IndexCoordinates.of(AlertAnalysisJobDocument.INDEX_NAME)
            );
            return true;
        } catch (RuntimeException exception) {
            try {
                if (repository.existsById(job.alertId())) {
                    return false;
                }
            } catch (RuntimeException lookupException) {
                exception.addSuppressed(lookupException);
            }
            throw exception;
        }
    }

    @Override
    public Optional<AlertAnalysisJob> findById(String alertId) {
        return repository.findById(alertId).map(this::toDomain);
    }

    @Override
    public Optional<AlertAnalysisJob> tryClaim(
            String alertId,
            String runId,
            Instant now,
            Instant leaseUntil
    ) {
        return transition(
                alertId,
                job -> job.claim(runId, now, leaseUntil)
        );
    }

    @Override
    public boolean complete(
            String alertId,
            String runId,
            AlertAnalysisResponse result,
            Instant now
    ) {
        return transition(
                alertId,
                job -> job.complete(runId, result, now)
        ).isPresent();
    }

    @Override
    public boolean markRetry(
            String alertId,
            String runId,
            Instant nextRetryAt,
            String error,
            Instant now
    ) {
        return transition(
                alertId,
                job -> job.retry(runId, nextRetryAt, error, now)
        ).isPresent();
    }

    @Override
    public boolean markDead(
            String alertId,
            String runId,
            String error,
            Instant now
    ) {
        return transition(
                alertId,
                job -> job.dead(runId, error, now)
        ).isPresent();
    }

    @Override
    public List<String> findRecoverableIds(Instant now, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        Pageable pageable = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Direction.ASC, "updatedAt")
        );

        Set<String> ids = new LinkedHashSet<>();
        addUntilLimit(
                ids,
                repository.findByStatus(AlertAnalysisJobStatus.PENDING, pageable),
                limit
        );
        addUntilLimit(
                ids,
                repository.findByStatusAndNextRetryAtLessThanEqual(
                        AlertAnalysisJobStatus.RETRY_WAIT,
                        now,
                        pageable
                ),
                limit
        );
        addUntilLimit(
                ids,
                repository.findByStatusAndLeaseUntilLessThanEqual(
                        AlertAnalysisJobStatus.RUNNING,
                        now,
                        pageable
                ),
                limit
        );

        return ids.stream().limit(limit).toList();
    }

    private Optional<AlertAnalysisJob> transition(
            String alertId,
            Function<AlertAnalysisJob, Optional<AlertAnalysisJob>> stateChange
    ) {
        OptimisticLockingFailureException lastConflict = null;

        for (int attempt = 0; attempt < optimisticRetries; attempt++) {
            Optional<AlertAnalysisJobDocument> existing = repository.findById(alertId);
            if (existing.isEmpty()) {
                return Optional.empty();
            }

            AlertAnalysisJobDocument currentDocument = existing.get();
            Optional<AlertAnalysisJob> changed = stateChange.apply(toDomain(currentDocument));
            if (changed.isEmpty()) {
                return Optional.empty();
            }

            AlertAnalysisJobDocument updated = toDocument(
                    changed.get(),
                    currentDocument.getSeqNoPrimaryTerm()
            );

            try {
                repository.save(updated);
                return changed;
            } catch (OptimisticLockingFailureException conflict) {
                lastConflict = conflict;
            }
        }

        if (lastConflict != null) {
            throw lastConflict;
        }
        return Optional.empty();
    }

    private void addUntilLimit(
            Set<String> target,
            List<AlertAnalysisJobDocument> documents,
            int limit
    ) {
        for (AlertAnalysisJobDocument document : documents) {
            if (target.size() >= limit) {
                return;
            }
            target.add(document.getAlertId());
        }
    }

    private AlertAnalysisJobDocument toDocument(
            AlertAnalysisJob job,
            SeqNoPrimaryTerm seqNoPrimaryTerm
    ) {
        AlertAnalysisJobDocument document = new AlertAnalysisJobDocument();
        document.setAlertId(job.alertId());
        document.setRequestJson(writeJson(job.request()));
        document.setStatus(job.status());
        document.setRunId(job.runId());
        document.setAttempt(job.attempt());
        document.setLeaseUntil(job.leaseUntil());
        document.setNextRetryAt(job.nextRetryAt());
        document.setResultJson(job.result() == null ? null : writeJson(job.result()));
        document.setLastError(job.lastError());
        document.setCreatedAt(job.createdAt());
        document.setUpdatedAt(job.updatedAt());
        document.setSeqNoPrimaryTerm(seqNoPrimaryTerm);
        return document;
    }

    private AlertAnalysisJob toDomain(AlertAnalysisJobDocument document) {
        AlertAnalysisRequest request = readJson(
                document.getRequestJson(),
                AlertAnalysisRequest.class
        );

        AlertAnalysisResponse result = document.getResultJson() == null
                ? null
                : readJson(document.getResultJson(), AlertAnalysisResponse.class);

        return new AlertAnalysisJob(
                document.getAlertId(),
                request,
                document.getStatus(),
                document.getRunId(),
                document.getAttempt(),
                document.getLeaseUntil(),
                document.getNextRetryAt(),
                result,
                document.getLastError(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize analysis job payload", exception);
        }
    }

    private <T> T readJson(String json, Class<T> targetType) {
        try {
            return objectMapper.readValue(json, targetType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize analysis job payload", exception);
        }
    }
}
