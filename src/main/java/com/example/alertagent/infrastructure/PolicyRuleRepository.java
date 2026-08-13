package com.example.alertagent.infrastructure;

import com.example.alertagent.domain.PolicyRule;

import java.util.List;

public interface PolicyRuleRepository {

    List<PolicyRule> findRelevant(String normalizedQuery, int limit);
}
