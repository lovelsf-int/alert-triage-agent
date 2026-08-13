package com.example.alertagent.application;

import com.example.alertagent.domain.EnrichedAlertContext;
import com.example.alertagent.support.AgentInvocationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AgentPromptFactory {

    private final ObjectMapper objectMapper;

    public AgentPromptFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String create(EnrichedAlertContext context) {
        try {
            String contextJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
            return """
                    请研判下面的企业安全告警。

                    <untrusted_alert_data>
                    %s
                    </untrusted_alert_data>

                    只允许返回下列结构的 JSON 对象：
                    {
                      "verdict": "TRUE_POSITIVE | FALSE_POSITIVE | INSUFFICIENT_EVIDENCE",
                      "confidence": 0.0,
                      "severity": "LOW | MEDIUM | HIGH | CRITICAL",
                      "summary": "一段简明结论",
                      "evidence": [
                        {
                          "sourceType": "ALERT | HISTORICAL_CASE | POLICY",
                          "sourceId": "必须是输入中真实存在的 alertId、caseId 或 policyId",
                          "fact": "该来源直接支持的事实"
                        }
                      ],
                      "matchedCaseIds": ["输入中真实存在的 caseId"],
                      "policyReferences": ["输入中真实存在的 policyId"],
                      "recommendedActions": ["只写建议，不得声称已经执行"],
                      "requiresHumanReview": true,
                      "rationale": "说明事实、案例、制度如何共同支持结论"
                    }

                    研判约束：
                    - confidence 必须在 0 到 1 之间。
                    - 没有足够证据时使用 INSUFFICIENT_EVIDENCE，且 requiresHumanReview=true。
                    - 不得引用输入中不存在的案例、制度、日志、IP、账户或资产。
                    - JSON 之外不得输出任何内容。
                    """.formatted(contextJson);
        }
        catch (JsonProcessingException exception) {
            throw new AgentInvocationException("Failed to serialize alert context", exception);
        }
    }
}
