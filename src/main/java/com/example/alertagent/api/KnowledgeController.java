package com.example.alertagent.api;

import com.example.alertagent.knowledge.KnowledgeDocumentRequest;
import com.example.alertagent.knowledge.KnowledgeIndexResult;
import com.example.alertagent.knowledge.KnowledgeIndexService;
import com.example.alertagent.knowledge.KnowledgeSearchResponse;
import com.example.alertagent.knowledge.KnowledgeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge")
@Validated
public class KnowledgeController {

    private final KnowledgeIndexService knowledgeIndexService;

    public KnowledgeController(KnowledgeIndexService knowledgeIndexService) {
        this.knowledgeIndexService = knowledgeIndexService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeIndexResult upsert(@Valid @RequestBody KnowledgeDocumentRequest request) {
        return knowledgeIndexService.upsert(request);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<KnowledgeIndexResult> upsertBatch(
            @RequestBody @Size(min = 1, max = 100) List<@Valid KnowledgeDocumentRequest> requests
    ) {
        return knowledgeIndexService.upsertAll(requests);
    }

    @PostMapping("/reindex-seed")
    public List<KnowledgeIndexResult> reindexSeed() {
        return knowledgeIndexService.reindexSeed();
    }

    @GetMapping("/search")
    public KnowledgeSearchResponse search(
            @RequestParam @NotBlank String query,
            @RequestParam(required = false) KnowledgeType type,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int topK
    ) {
        return knowledgeIndexService.search(query, type, topK);
    }

    @DeleteMapping("/{type}/{sourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable KnowledgeType type,
            @PathVariable @NotBlank String sourceId,
            @RequestParam(defaultValue = "1") String version
    ) {
        knowledgeIndexService.delete(type, sourceId, version);
    }
}
