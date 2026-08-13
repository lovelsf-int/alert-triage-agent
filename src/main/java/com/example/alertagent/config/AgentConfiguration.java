package com.example.alertagent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "alert-agent",
            name = "engine",
            havingValue = "react-agent",
            matchIfMissing = true
    )
    public ReactAgent alertTriageReactAgent(ChatModel chatModel) throws Exception {
        return ReactAgent.builder()
                .name("security_alert_triage_agent")
                .description("Analyzes security alerts using retrieved cases and policies")
                .model(chatModel)
                .systemPrompt(AgentSystemPrompt.VALUE)
                .build();
    }
}
