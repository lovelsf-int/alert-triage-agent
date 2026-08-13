package com.example.alertagent.knowledge;

import com.example.alertagent.domain.HistoricalCase;
import com.example.alertagent.domain.PolicyRule;
import com.example.alertagent.domain.Verdict;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KnowledgeDocumentMapper {

    static final String META_TYPE = "knowledgeType";
    static final String META_SOURCE_ID = "sourceId";
    static final String META_TITLE = "title";
    static final String META_BODY = "body";
    static final String META_VERSION = "version";
    static final String META_ACTIVE = "active";
    static final String META_VERDICT = "verdict";
    static final String META_KEYWORDS = "keywordsJson";
    static final String META_ACTIONS = "recommendedActionsJson";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public KnowledgeDocumentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
    }

    public Document toDocument(KnowledgeDocumentRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(META_TYPE, request.type().name());
        metadata.put(META_SOURCE_ID, request.sourceId());
        metadata.put(META_TITLE, request.title());
        metadata.put(META_BODY, request.content());
        metadata.put(META_VERSION, request.version());
        metadata.put(META_ACTIVE, Boolean.TRUE.equals(request.active()));
        metadata.put(META_VERDICT, request.verdict() == null ? "" : request.verdict().name());
        metadata.put(META_KEYWORDS, writeList(request.keywords()));
        metadata.put(META_ACTIONS, writeList(request.recommendedActions()));

        return new Document(
                documentId(request.type(), request.sourceId(), request.version()),
                embeddingText(request),
                metadata
        );
    }

    public HistoricalCase toHistoricalCase(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new HistoricalCase(
                text(metadata, META_SOURCE_ID),
                text(metadata, META_TITLE),
                text(metadata, META_BODY),
                parseVerdict(text(metadata, META_VERDICT)),
                readList(text(metadata, META_KEYWORDS)),
                document.getScore()
        );
    }

    public PolicyRule toPolicyRule(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new PolicyRule(
                text(metadata, META_SOURCE_ID),
                text(metadata, META_TITLE),
                text(metadata, META_BODY),
                readList(text(metadata, META_ACTIONS)),
                readList(text(metadata, META_KEYWORDS)),
                document.getScore()
        );
    }

    public KnowledgeSearchHit toSearchHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new KnowledgeSearchHit(
                document.getId(),
                KnowledgeType.valueOf(text(metadata, META_TYPE)),
                text(metadata, META_SOURCE_ID),
                text(metadata, META_TITLE),
                text(metadata, META_BODY),
                text(metadata, META_VERSION),
                booleanValue(metadata.get(META_ACTIVE)),
                document.getScore(),
                metadata
        );
    }

    public String documentId(KnowledgeType type, String sourceId, String version) {
        String source = type.name() + ":" + sourceId.trim() + ":" + normalizeVersion(version);
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String embeddingText(KnowledgeDocumentRequest request) {
        StringBuilder text = new StringBuilder()
                .append("知识类型：").append(request.type().name()).append('\n')
                .append("标题：").append(request.title()).append('\n')
                .append("正文：").append(request.content()).append('\n');
        if (!request.keywords().isEmpty()) {
            text.append("关键词：").append(String.join("、", request.keywords())).append('\n');
        }
        if (request.verdict() != null) {
            text.append("历史结论：").append(request.verdict().name()).append('\n');
        }
        if (!request.recommendedActions().isEmpty()) {
            text.append("建议动作：").append(String.join("；", request.recommendedActions())).append('\n');
        }
        return text.toString();
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize knowledge metadata", exception);
        }
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(value, STRING_LIST));
        }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid list metadata in vector document", exception);
        }
    }

    private Verdict parseVerdict(String value) {
        if (value == null || value.isBlank()) {
            return Verdict.INSUFFICIENT_EVIDENCE;
        }
        return Verdict.valueOf(value);
    }

    private String text(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private String normalizeVersion(String version) {
        return version == null || version.isBlank() ? "1" : version.trim();
    }
}
