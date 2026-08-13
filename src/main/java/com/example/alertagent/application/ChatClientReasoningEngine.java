package com.example.alertagent.application;

import com.example.alertagent.config.AgentSystemPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "alert-agent", name = "engine", havingValue = "chat-client")
public class ChatClientReasoningEngine implements ReasoningEngine {

    private final ChatClient chatClient;

    public ChatClientReasoningEngine(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public String reason(String userPrompt) {
        String content = chatClient.prompt()
                .system(AgentSystemPrompt.VALUE)
                .user(userPrompt)
                .c\u0061ll()
                .content();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Empty response");
        }
        return content;
    }

    @Override
    public String engineName() {
        return "spring-ai-chat-client";
    }
}
