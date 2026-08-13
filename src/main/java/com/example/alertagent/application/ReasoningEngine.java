package com.example.alertagent.application;

/** Abstraction over the configured chat reasoning runtime. */
public interface ReasoningEngine {

    String reason(String userPrompt);

    String engineName();
}
