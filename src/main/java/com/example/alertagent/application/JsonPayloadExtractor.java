package com.example.alertagent.application;

import com.example.alertagent.support.AgentOutputException;
import org.springframework.stereotype.Component;

@Component
public class JsonPayloadExtractor {

    public String extractObject(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new AgentOutputException("Model output is empty");
        }

        String normalized = rawResponse.trim();
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');

        if (start < 0 || end <= start) {
            throw new AgentOutputException("Model output does not contain a JSON object");
        }
        return normalized.substring(start, end + 1);
    }
}
