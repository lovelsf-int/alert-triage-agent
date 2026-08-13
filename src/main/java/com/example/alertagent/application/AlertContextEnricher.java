package com.example.alertagent.application;

import com.example.alertagent.config.AgentProperties;
import com.example.alertagent.domain.AlertAnalysisRequest;
import com.example.alertagent.domain.EnrichedAlertContext;
import com.example.alertagent.infrastructure.HistoricalCaseRepository;
import com.example.alertagent.infrastructure.PolicyRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AlertContextEnricher {

    private final HistoricalCaseRepository historicalCaseRepository;
    private final PolicyRuleRepository policyRuleRepository;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public AlertContextEnricher(
            HistoricalCaseRepository historicalCaseRepository,
            PolicyRuleRepository policyRuleRepository,
            AgentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.historicalCaseRepository = historicalCaseRepository;
        this.policyRuleRepository = policyRuleRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public EnrichedAlertContext enrich(AlertAnalysisRequest alert) {
        String query = buildQuery(alert);
        return new EnrichedAlertContext(
                alert,
                query,
                historicalCaseRepository.findRelevant(query, properties.getMaxHistoricalCases()),
                policyRuleRepository.findRelevant(query, properties.getMaxPolicyRules())
        );
    }

    private String buildQuery(AlertAnalysisRequest alert) {
        try {
            return String.join(" ",
                    alert.alertType(),
                    alert.title(),
                    alert.description(),
                    alert.severity().name(),
                    alert.source(),
                    alert.assetId(),
                    objectMapper.writeValueAsString(alert.attributes())
            );
        }
        catch (JsonProcessingException exception) {
            return String.join(" ",
                    alert.alertType(),
                    alert.title(),
                    alert.description(),
                    alert.severity().name(),
                    alert.source(),
                    alert.assetId()
            );
        }
    }
}
