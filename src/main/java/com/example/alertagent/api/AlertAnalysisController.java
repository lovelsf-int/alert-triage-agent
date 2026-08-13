package com.example.alertagent.api;

import com.example.alertagent.application.AlertAnalysisService;
import com.example.alertagent.domain.AlertAnalysisRequest;
import com.example.alertagent.domain.AlertAnalysisResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alert-analyses")
public class AlertAnalysisController {

    private final AlertAnalysisService alertAnalysisService;

    public AlertAnalysisController(AlertAnalysisService alertAnalysisService) {
        this.alertAnalysisService = alertAnalysisService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertAnalysisResponse analyze(@Valid @RequestBody AlertAnalysisRequest request) {
        return alertAnalysisService.analyze(request);
    }
}
