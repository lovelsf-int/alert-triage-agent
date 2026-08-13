package com.example.alertagent.knowledge;

import com.example.alertagent.domain.Verdict;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnowledgeDocumentRequest(
        @NotNull KnowledgeType type,
        @NotBlank @Size(max = 256) String sourceId,
        @NotBlank @Size(max = 1000) String title,
        @NotBlank @Size(max = 30000) String content,
        Verdict verdict,
        List<@Size(max = 256) String> keywords,
        List<@Size(max = 1000) String> recommendedActions,
        @Size(max = 64) String version,
        Boolean active
) {
    public KnowledgeDocumentRequest {
        sourceId = trim(sourceId);
        title = trim(title);
        content = trim(content);
        keywords = clean(keywords);
        recommendedActions = clean(recommendedActions);
        version = version == null || version.isBlank() ? "1" : version.trim();
        active = active == null ? Boolean.TRUE : active;
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
