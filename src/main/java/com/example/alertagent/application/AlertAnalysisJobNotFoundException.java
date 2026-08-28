package com.example.alertagent.application;

public class AlertAnalysisJobNotFoundException extends RuntimeException {

    public AlertAnalysisJobNotFoundException(String alertId) {
        super("Alert analysis job not found: " + alertId);
    }
}
