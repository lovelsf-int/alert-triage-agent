package com.example.alertagent.domain;

import java.util.List;

public record EnrichedAlertContext(
        AlertAnalysisRequest alert,
        String retrievalQuery,
        List<HistoricalCase> historicalCases,
        List<PolicyRule> policyRules
) {
    public EnrichedAlertContext {
        retrievalQuery = retrievalQuery == null ? "" : retrievalQuery;
        historicalCases = historicalCases == null ? List.of() : List.copyOf(historicalCases);
        policyRules = policyRules == null ? List.of() : List.copyOf(policyRules);
    }
}
