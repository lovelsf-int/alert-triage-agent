package com.example.alertagent.infrastructure.elasticsearch;

import com.example.alertagent.domain.AlertAnalysisJobStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.query.SeqNoPrimaryTerm;

import java.time.Instant;

@Document(indexName = "alert-analysis-jobs-v1", createIndex = true)
public class AlertAnalysisJobDocument {

    public static final String INDEX_NAME = "alert-analysis-jobs-v1";

    @Id
    private String alertId;

    @Field(type = FieldType.Text, index = false)
    private String requestJson;

    @Field(type = FieldType.Keyword)
    private AlertAnalysisJobStatus status;

    @Field(type = FieldType.Keyword)
    private String runId;

    @Field(type = FieldType.Integer)
    private int attempt;

    @Field(type = FieldType.Date)
    private Instant leaseUntil;

    @Field(type = FieldType.Date)
    private Instant nextRetryAt;

    @Field(type = FieldType.Text, index = false)
    private String resultJson;

    @Field(type = FieldType.Text, index = false)
    private String lastError;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant updatedAt;

    private SeqNoPrimaryTerm seqNoPrimaryTerm;

    public AlertAnalysisJobDocument() {
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }

    public AlertAnalysisJobStatus getStatus() {
        return status;
    }

    public void setStatus(AlertAnalysisJobStatus status) {
        this.status = status;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(Instant leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public SeqNoPrimaryTerm getSeqNoPrimaryTerm() {
        return seqNoPrimaryTerm;
    }

    public void setSeqNoPrimaryTerm(SeqNoPrimaryTerm seqNoPrimaryTerm) {
        this.seqNoPrimaryTerm = seqNoPrimaryTerm;
    }
}
