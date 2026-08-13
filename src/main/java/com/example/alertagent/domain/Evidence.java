package com.example.alertagent.domain;

import java.util.Objects;

public record Evidence(
        EvidenceSourceType sourceType,
        String sourceId,
        String fact
) {
    public Evidence {
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        sourceId = requireText(sourceId, "sourceId");
        fact = requireText(fact, "fact");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
