package com.example.alertagent.config;

import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.example.alertagent.knowledge.TaskAwareDashScopeEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {

    @Bean
    @Primary
    public EmbeddingModel taskAwareEmbeddingModel(
            DashScopeEmbeddingModel dashScopeEmbeddingModel,
            RagProperties properties
    ) {
        return new TaskAwareDashScopeEmbeddingModel(
                dashScopeEmbeddingModel,
                properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(),
                properties.getIngestBatchSize()
        );
    }
}
