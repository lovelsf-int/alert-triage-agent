package com.example.alertagent.api;

import com.example.alertagent.application.AlertAnalysisService;
import com.example.alertagent.application.AsyncAlertAnalysisService;
import com.example.alertagent.domain.AlertAnalysisJobView;
import com.example.alertagent.domain.AlertAnalysisRequest;
import com.example.alertagent.domain.AlertAnalysisResponse;
import com.example.alertagent.domain.AlertAnalysisSubmissionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/alert-analyses")
public class AlertAnalysisController {

    private final AsyncAlertAnalysisService asyncAlertAnalysisService;
    private final AlertAnalysisService alertAnalysisService;

    public AlertAnalysisController(
            AsyncAlertAnalysisService asyncAlertAnalysisService,
            AlertAnalysisService alertAnalysisService
    ) {
        this.asyncAlertAnalysisService = asyncAlertAnalysisService;
        this.alertAnalysisService = alertAnalysisService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AlertAnalysisSubmissionResponse analyze(
            @Valid @RequestBody AlertAnalysisRequest request
    ) {
        return asyncAlertAnalysisService.submit(request);
    }

    @GetMapping("/{alertId}")
    public AlertAnalysisJobView getAnalysisJob(
            @PathVariable @NotBlank @Size(max = 128) String alertId
    ) {
        return asyncAlertAnalysisService.get(alertId);
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertAnalysisResponse analyzeSynchronously(
            @Valid @RequestBody AlertAnalysisRequest request
    ) {
        return alertAnalysisService.analyze(request);
    }
}
