package com.example.alertagent.application;

public interface ReasoningEngine {

    String reason(String userPrompt);

    String engineName();
}
