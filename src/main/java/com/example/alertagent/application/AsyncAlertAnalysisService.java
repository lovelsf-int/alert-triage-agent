package com.example.alertagent.application;

import com.example.alertagent.domain.AlertAnalysisJob;
import com.example.alertagent.domain.AlertAnalysisJobView;
import com.example.alertagent.domain.AlertAnalysisRequest;
import com.example.alertagent.domain.AlertAnalysisSubmissionResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class AsyncAlertAnalysisService {

    private static final String STATUS_PATH = "/api/v1/alert-analyses/";

    private final AlertAnalysisJobStore jobStore;
    private final AlertAnalysisTaskDispatcher dispatcher;
    private final Clock clock;

    public AsyncAlertAnalysisService(
            AlertAnalysisJobStore jobStore,
            AlertAnalysisTaskDispatcher dispatcher,
            @Qualifier("alertAnalysisClock") Clock clock
    ) {
        this.jobStore = jobStore;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    public AlertAnalysisSubmissionResponse submit(AlertAnalysisRequest request) {
        Instant now = clock.instant();
        boolean created = jobStore.createPending(AlertAnalysisJob.pending(request, now));

        AlertAnalysisJob current = jobStore.findById(request.alertId())
                .orElseThrow(() -> new IllegalStateException(
                        "Elasticsearch did not return the persisted analysis job: " + request.alertId()
                ));

        boolean dispatched = current.isClaimable(now) && dispatcher.dispatch(request.alertId());

        return new AlertAnalysisSubmissionResponse(
                request.alertId(),
                current.status(),
                created,
                dispatched,
                now,
                STATUS_PATH + request.alertId()
        );
    }

    public AlertAnalysisJobView get(String alertId) {
        return jobStore.findById(alertId)
                .map(AlertAnalysisJob::toView)
                .orElseThrow(() -> new AlertAnalysisJobNotFoundException(alertId));
    }
}
