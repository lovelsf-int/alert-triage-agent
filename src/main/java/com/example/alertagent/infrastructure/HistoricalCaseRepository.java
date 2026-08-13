package com.example.alertagent.infrastructure;

import com.example.alertagent.domain.HistoricalCase;

import java.util.List;

public interface HistoricalCaseRepository {

    List<HistoricalCase> findRelevant(String normalizedQuery, int limit);
}
