package com.example.alertagent.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

@Component
public class KnowledgeSeedLoader {

    private static final TypeReference<List<KnowledgeDocumentRequest>> KNOWLEDGE_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public KnowledgeSeedLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);
    }

    public List<KnowledgeDocumentRequest> load() {
        ClassPathResource resource = new ClassPathResource("knowledge/seed.b64");
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] json = Base64.getDecoder().decode(inputStream.readAllBytes());
            return List.copyOf(objectMapper.readValue(json, KNOWLEDGE_LIST));
        }
        catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Knowledge seed could not be loaded", exception);
        }
    }
}
