package com.example.alertagent.infrastructure;

import com.example.alertagent.config.RagProperties;
import com.example.alertagent.domain.PolicyRule;
import com.example.alertagent.knowledge.KnowledgeDocumentMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VectorPolicyRuleRepository implements PolicyRuleRepository {

    private static final String FILTER = "knowledgeType == 'POLICY_RULE' && active == true";

    private final VectorStore vectorStore;
    private final KnowledgeDocumentMapper mapper;
    private final RagProperties properties;

    public VectorPolicyRuleRepository(
            VectorStore vectorStore,
            KnowledgeDocumentMapper mapper,
            RagProperties properties
    ) {
        this.vectorStore = vectorStore;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public List<PolicyRule> findRelevant(String normalizedQuery, int limit) {
        if (limit <= 0 || normalizedQuery == null || normalizedQuery.isBlank()) {
            return List.of();
        }
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(normalizedQuery)
                .topK(limit)
                .similarityThreshold(properties.getSimilarityThreshold())
                .filterExpression(FILTER)
                .build());
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream().map(mapper::toPolicyRule).toList();
    }
}
