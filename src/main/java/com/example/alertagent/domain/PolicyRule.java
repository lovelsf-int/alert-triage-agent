package com.example.alertagent.domain;

import java.util.List;

public record PolicyRule(
        String policyId,
        String name,
        String requirement,
        List<String> recommendedActions,
        List<String> keywords,
        Double retrievalScore
) {
    public PolicyRule {
        recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public PolicyRule(
            String policyId,
            String name,
            String requirement,
            List<String> recommendedActions,
            List<String> keywords
    ) {
        this(policyId, name, requirement, recommendedActions, keywords, null);
    }
}
