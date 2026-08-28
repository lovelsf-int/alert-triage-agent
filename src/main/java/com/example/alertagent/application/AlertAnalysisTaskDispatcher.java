package com.example.alertagent.application;

import com.example.alertagent.config.AsyncAnalysisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Component
public class AlertAnalysisTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlertAnalysisTaskDispatcher.class);

    private final ExecutorService executorService;
    private final AlertAnalysisWorker worker;
    private final Semaphore inFlightSlots;

    public AlertAnalysisTaskDispatcher(
            @Qualifier("alertAnalysisVirtualThreadExecutor") ExecutorService executorService,
            AlertAnalysisWorker worker,
            AsyncAnalysisProperties properties
    ) {
        this.executorService = executorService;
        this.worker = worker;
        this.inFlightSlots = new Semaphore(properties.getMaxInFlight());
    }

    public boolean dispatch(String alertId) {
        if (!inFlightSlots.tryAcquire()) {
            return false;
        }

        try {
            executorService.execute(() -> {
                try {
                    worker.process(alertId);
                } catch (RuntimeException exception) {
                    log.error("Unhandled asynchronous analysis error: alertId={}", alertId, exception);
                } finally {
                    inFlightSlots.release();
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            inFlightSlots.release();
            log.warn("Virtual-thread executor rejected analysis: alertId={}", alertId, exception);
            return false;
        }
    }

    public int availableSlots() {
        return inFlightSlots.availablePermits();
    }
}
