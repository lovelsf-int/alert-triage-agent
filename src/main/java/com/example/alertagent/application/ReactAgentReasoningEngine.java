package com.example.alertagent.application;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.example.alertagent.support.AgentInvocationException;
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
        try {
            AssistantMessage response = reactAgent.call(userPrompt);
            if (response == null || response.getText() == null || response.getText().isBlank()) {
                throw new AgentInvocationException("ReactAgent returned an empty response");
            }
            return response.getText();
        }
        catch (AgentInvocationException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new AgentInvocationException("ReactAgent invocation failed", exception);
        }
    }

    @Override
    public String engineName() {
        return "spring-ai-alibaba-react-agent";
    }
}
