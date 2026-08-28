package com.example.alertagent.application;

import com.example.alertagent.config.AsyncAnalysisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

@Component
public class AlertAnalysisRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertAnalysisRecoveryScheduler.class);

    private final AlertAnalysisJobStore jobStore;
    private final AlertAnalysisTaskDispatcher dispatcher;
    private final AsyncAnalysisProperties properties;
    private final Clock clock;

    public AlertAnalysisRecoveryScheduler(
            AlertAnalysisJobStore jobStore,
            AlertAnalysisTaskDispatcher dispatcher,
            AsyncAnalysisProperties properties,
            @Qualifier("alertAnalysisClock") Clock clock
    ) {
        this.jobStore = jobStore;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${alert-agent.async.recovery-interval-ms:5000}")
    public void recover() {
        int availableSlots = dispatcher.availableSlots();
        if (availableSlots <= 0) {
            return;
        }

        int limit = Math.min(availableSlots, properties.getRecoveryBatchSize());

        try {
            List<String> alertIds = jobStore.findRecoverableIds(clock.instant(), limit);
            for (String alertId : alertIds) {
                if (!dispatcher.dispatch(alertId)) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            log.error("Failed to scan Elasticsearch for recoverable AI analysis jobs", exception);
        }
    }
}
