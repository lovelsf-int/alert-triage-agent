package com.example.alertagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "alert-agent.rag",
        name = "seed-on-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class KnowledgeIndexBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexBootstrap.class);

    private final KnowledgeIndexService knowledgeIndexService;

    public KnowledgeIndexBootstrap(KnowledgeIndexService knowledgeIndexService) {
        this.knowledgeIndexService = knowledgeIndexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<KnowledgeIndexResult> results = knowledgeIndexService.reindexSeed();
        log.info("Vector knowledge seed indexed: documents={}", results.size());
    }
}
