package com.example.alertagent.knowledge;

import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

public final class TaskAwareDashScopeEmbeddingModel implements EmbeddingModel {

    private final DashScopeEmbeddingModel delegate;
    private final String model;
    private final int dimensions;
    private final int maxBatchSize;

    public TaskAwareDashScopeEmbeddingModel(
            DashScopeEmbeddingModel delegate,
            String model,
            int dimensions,
            int maxBatchSize
    ) {
        Assert.notNull(delegate, "delegate must not be null");
        Assert.hasText(model, "model must not be blank");
        Assert.isTrue(dimensions > 0, "dimensions must be positive");
        Assert.isTrue(maxBatchSize > 0, "maxBatchSize must be positive");
        this.delegate = delegate;
        this.model = model;
        this.dimensions = dimensions;
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Assert.notNull(request, "request must not be null");
        if (request.getOptions() instanceof DashScopeEmbeddingOptions) {
            return delegate.call(request);
        }
        return invoke(request.getInstructions(), "document");
    }

    @Override
    public float[] embed(String text) {
        Assert.hasText(text, "text must not be blank");
        return invoke(List.of(text), "query").getResult().getOutput();
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "document must not be null");
        return invoke(List.of(requireText(document)), "document").getResult().getOutput();
    }

    @Override
    public List<float[]> embed(
            List<Document> documents,
            EmbeddingOptions options,
            BatchingStrategy batchingStrategy
    ) {
        Assert.notNull(documents, "documents must not be null");
        Assert.notNull(batchingStrategy, "batchingStrategy must not be null");
        List<float[]> vectors = new ArrayList<>(documents.size());
        for (List<Document> semanticBatch : batchingStrategy.batch(documents)) {
            List<String> texts = semanticBatch.stream().map(this::requireText).toList();
            for (List<String> batch : partition(texts)) {
                vectors.addAll(invoke(batch, "document").getResults().stream()
                        .map(Embedding::getOutput)
                        .toList());
            }
        }
        Assert.isTrue(vectors.size() == documents.size(), "embedding count mismatch");
        return vectors;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private EmbeddingResponse invoke(List<String> texts, String textType) {
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(model)
                .dimensions(dimensions)
                .textType(textType)
                .build();
        return delegate.call(new EmbeddingRequest(texts, options));
    }

    private List<List<String>> partition(List<String> texts) {
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += maxBatchSize) {
            batches.add(texts.subList(start, Math.min(start + maxBatchSize, texts.size())));
        }
        return batches;
    }

    private String requireText(Document document) {
        String text = document.getText();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("document text must not be blank");
        }
        return text;
    }
}
