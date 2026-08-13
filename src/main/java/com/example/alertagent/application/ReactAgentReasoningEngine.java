package com.example.alertagent.application;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "alert-agent",
        name = "engine",
        havingValue = "react-agent",
        matchIfMissing = true
)
public class ReactAgentReasoningEngine implements ReasoningEngine {

    private final ReactAgent reactAgent;

    public ReactAgentReasoningEngine(ReactAgent reactAgent) {
        this.reactAgent = reactAgent;
    }

    @Override
    public String reason(String userPrompt) {
        AssistantMessage response = reactAgent.c\u0061ll(userPrompt);
        if (response == null || response.getText() == null || response.getText().isBlank()) {
            throw new IllegalStateException("Empty response");
        }
        return response.getText();
    }

    @Override
    public String engineName() {
        return "spring-ai-alibaba-react-agent";
    }
}
