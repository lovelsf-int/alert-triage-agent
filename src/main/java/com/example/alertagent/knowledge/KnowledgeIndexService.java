package com.example.alertagent.knowledge;

import com.example.alertagent.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeIndexService {

    private final VectorStore vectorStore;
    private final KnowledgeDocumentMapper mapper;
    private final KnowledgeSeedLoader seedLoader;
    private final RagProperties properties;

    public KnowledgeIndexService(
            VectorStore vectorStore,
            KnowledgeDocumentMapper mapper,
            KnowledgeSeedLoader seedLoader,
            RagProperties properties
    ) {
        this.vectorStore = vectorStore;
        this.mapper = mapper;
        this.seedLoader = seedLoader;
        this.properties = properties;
    }

    public KnowledgeIndexResult upsert(KnowledgeDocumentRequest request) {
        return upsertAll(List.of(request)).getFirst();
    }

    public List<KnowledgeIndexResult> upsertAll(List<KnowledgeDocumentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<Document> documents = requests.stream().map(mapper::toDocument).toList();
        for (List<Document> batch : partition(documents, properties.getIngestBatchSize())) {
            vectorStore.add(batch);
        }

        Instant indexedAt = Instant.now();
        List<KnowledgeIndexResult> results = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            KnowledgeDocumentRequest request = requests.get(index);
            results.add(new KnowledgeIndexResult(
                    documents.get(index).getId(),
                    request.type(),
                    request.sourceId(),
                    request.version(),
                    indexedAt
            ));
        }
        return List.copyOf(results);
    }

    public void delete(KnowledgeType type, String sourceId, String version) {
        vectorStore.delete(List.of(mapper.documentId(type, sourceId, version)));
    }

    public KnowledgeSearchResponse search(String query, KnowledgeType type, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK < 1 || topK > 50) {
            throw new IllegalArgumentException("topK must be in [1, 50]");
        }

        SearchRequest.Builder request = SearchRequest.builder()
                .query(query.trim())
                .topK(topK)
                .similarityThreshold(properties.getSimilarityThreshold());

        String filter = type == null
                ? "active == true"
                : "knowledgeType == '" + type.name() + "' && active == true";
        request.filterExpression(filter);

        List<Document> documents = vectorStore.similaritySearch(request.build());
        List<KnowledgeSearchHit> hits = documents == null
                ? List.of()
                : documents.stream().map(mapper::toSearchHit).toList();

        return new KnowledgeSearchResponse(
                query.trim(),
                type,
                topK,
                properties.getSimilarityThreshold(),
                hits
        );
    }

    public List<KnowledgeIndexResult> reindexSeed() {
        return upsertAll(seedLoader.load());
    }

    private <T> List<List<T>> partition(List<T> values, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int start = 0; start < values.size(); start += batchSize) {
            batches.add(values.subList(start, Math.min(start + batchSize, values.size())));
        }
        return batches;
    }
}
