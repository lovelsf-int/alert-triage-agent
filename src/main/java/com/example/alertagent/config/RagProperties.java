package com.example.alertagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alert-agent.rag")
public class RagProperties {

    private double similarityThreshold = 0.35d;
    private boolean seedOnStartup = true;
    private int ingestBatchSize = 10;
    private String embeddingModel = "text-embedding-v4";
    private int embeddingDimensions = 1024;
    private String vectorStore = "pgvector";

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        if (similarityThreshold < 0.0d || similarityThreshold > 1.0d) {
            throw new IllegalArgumentException("similarityThreshold must be in [0, 1]");
        }
        this.similarityThreshold = similarityThreshold;
    }

    public boolean isSeedOnStartup() {
        return seedOnStartup;
    }

    public void setSeedOnStartup(boolean seedOnStartup) {
        this.seedOnStartup = seedOnStartup;
    }

    public int getIngestBatchSize() {
        return ingestBatchSize;
    }

    public void setIngestBatchSize(int ingestBatchSize) {
        if (ingestBatchSize < 1 || ingestBatchSize > 10) {
            throw new IllegalArgumentException("ingestBatchSize must be in [1, 10] for text-embedding-v4");
        }
        this.ingestBatchSize = ingestBatchSize;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("embeddingModel must not be blank");
        }
        this.embeddingModel = embeddingModel.trim();
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        if (embeddingDimensions < 1) {
            throw new IllegalArgumentException("embeddingDimensions must be positive");
        }
        this.embeddingDimensions = embeddingDimensions;
    }

    public String getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(String vectorStore) {
        if (vectorStore == null || vectorStore.isBlank()) {
            throw new IllegalArgumentException("vectorStore must not be blank");
        }
        this.vectorStore = vectorStore.trim();
    }
}
