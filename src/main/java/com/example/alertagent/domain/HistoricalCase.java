package com.example.alertagent.domain;

import java.util.List;

public record HistoricalCase(
        String caseId,
        String title,
        String summary,
        Verdict verdict,
        List<String> keywords,
        Double retrievalScore
) {
    public HistoricalCase {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public HistoricalCase(
            String caseId,
            String title,
            String summary,
            Verdict verdict,
            List<String> keywords
    ) {
        this(caseId, title, summary, verdict, keywords, null);
    }
}
