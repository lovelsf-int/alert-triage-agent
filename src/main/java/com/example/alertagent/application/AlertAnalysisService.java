package com.example.alertagent.application;

import com.example.alertagent.config.AgentProperties;
import com.example.alertagent.config.RagProperties;
import com.example.alertagent.domain.AlertAnalysisRequest;
import com.example.alertagent.domain.AlertAnalysisResponse;
import com.example.alertagent.domain.AlertAssessment;
import com.example.alertagent.domain.EnrichedAlertContext;
import com.example.alertagent.domain.EvidenceSourceType;
import com.example.alertagent.domain.RetrievalHit;
import com.example.alertagent.domain.RetrievalTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AlertAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AlertAnalysisService.class);

    private final AlertContextEnricher contextEnricher;
    private final AgentPromptFactory promptFactory;
    private final ReasoningEngine reasoningEngine;
    private final AgentOutputParser outputParser;
    private final AgentProperties properties;
    private final RagProperties ragProperties;

    public AlertAnalysisService(
            AlertContextEnricher contextEnricher,
            AgentPromptFactory promptFactory,
            ReasoningEngine reasoningEngine,
            AgentOutputParser outputParser,
            AgentProperties properties,
            RagProperties ragProperties
    ) {
        this.contextEnricher = contextEnricher;
        this.promptFactory = promptFactory;
        this.reasoningEngine = reasoningEngine;
        this.outputParser = outputParser;
        this.properties = properties;
        this.ragProperties = ragProperties;
    }

    public AlertAnalysisResponse analyze(AlertAnalysisRequest request) {
        EnrichedAlertContext context = contextEnricher.enrich(request);
        String prompt = promptFactory.create(context);
        String rawOutput = reasoningEngine.reason(prompt);
        AlertAssessment assessment = outputParser.parse(rawOutput)
                .enforceGovernance(properties.getHumanReviewThreshold(), request.severity());

        String analysisId = UUID.randomUUID().toString();
        log.info(
                "Alert analysis completed: alertId={}, analysisId={}, verdict={}, confidence={}, humanReview={}, cases={}, policies={}",
                request.alertId(),
                analysisId,
                assessment.verdict(),
                assessment.confidence(),
                assessment.requiresHumanReview(),
                context.historicalCases().size(),
                context.policyRules().size()
        );

        return new AlertAnalysisResponse(
                request.alertId(),
                analysisId,
                Instant.now(),
                reasoningEngine.engineName(),
                retrievalTrace(context),
                assessment
        );
    }

    private RetrievalTrace retrievalTrace(EnrichedAlertContext context) {
        return new RetrievalTrace(
                context.retrievalQuery(),
                ragProperties.getEmbeddingModel(),
                ragProperties.getEmbeddingDimensions(),
                ragProperties.getVectorStore(),
                ragProperties.getSimilarityThreshold(),
                context.historicalCases().stream()
                        .map(item -> new RetrievalHit(
                                EvidenceSourceType.HISTORICAL_CASE,
                                item.caseId(),
                                item.title(),
                                item.retrievalScore()
                        ))
                        .toList(),
                context.policyRules().stream()
                        .map(item -> new RetrievalHit(
                                EvidenceSourceType.POLICY,
                                item.policyId(),
                                item.name(),
                                item.retrievalScore()
                        ))
                        .toList()
        );
    }
}
