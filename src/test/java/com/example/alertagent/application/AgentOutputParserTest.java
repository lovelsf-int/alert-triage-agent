package com.example.alertagent.application;

import com.example.alertagent.domain.AlertAssessment;
import com.example.alertagent.domain.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOutputParserTest {

    @Test
    void parsesCaseInsensitiveEnumValues() {
        AgentOutputParser parser = new AgentOutputParser(
                new JsonPayloadExtractor(),
                new ObjectMapper()
        );

        String raw = """
                {
                  "verdict": "true_positive",
                  "confidence": 0.91,
                  "severity": "high",
                  "summary": "Suspicious authentication",
                  "evidence": [{
                    "sourceType": "alert",
                    "sourceId": "ALT-1",
                    "fact": "Repeated failures"
                  }],
                  "matchedCaseIds": [],
                  "policyReferences": [],
                  "recommendedActions": ["Review"],
                  "requiresHumanReview": false,
                  "rationale": "Evidence supports the conclusion"
                }
                """;

        AlertAssessment assessment = parser.parse(raw);

        assertThat(assessment.verdict()).isEqualTo(Verdict.TRUE_POSITIVE);
        assertThat(assessment.confidence()).isEqualTo(0.91);
    }
}
