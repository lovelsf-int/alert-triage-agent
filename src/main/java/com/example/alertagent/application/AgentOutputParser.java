package com.example.alertagent.application;

import com.example.alertagent.domain.AlertAssessment;
import com.example.alertagent.support.AgentOutputException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AgentOutputParser {

    private final JsonPayloadExtractor extractor;
    private final ObjectMapper objectMapper;

    public AgentOutputParser(JsonPayloadExtractor extractor, ObjectMapper objectMapper) {
        this.extractor = extractor;
        this.objectMapper = objectMapper.copy()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);
    }

    public AlertAssessment parse(String rawResponse) {
        String json = extractor.extractObject(rawResponse);
        try {
            return objectMapper.readValue(json, AlertAssessment.class);
        }
        catch (Exception exception) {
            throw new AgentOutputException("Model output is not a valid AlertAssessment JSON", exception);
        }
    }
}
