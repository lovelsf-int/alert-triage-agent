package com.example.alertagent.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AlertAnalysisRequest(
        @NotBlank @Size(max = 128) String alertId,
        @NotBlank @Size(max = 128) String alertType,
        @NotBlank @Size(max = 512) String title,
        @NotBlank @Size(max = 12000) String description,
        AlertSeverity severity,
        @Size(max = 128) String source,
        @Size(max = 256) String assetId,
        Instant occurredAt,
        Map<String, Object> attributes
) {
    public AlertAnalysisRequest {
        severity = severity == null ? AlertSeverity.MEDIUM : severity;
        source = source == null || source.isBlank() ? "unknown" : source.trim();
        assetId = assetId == null || assetId.isBlank() ? "unknown" : assetId.trim();
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
