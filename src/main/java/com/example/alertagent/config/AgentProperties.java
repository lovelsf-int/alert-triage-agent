package com.example.alertagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alert-agent")
public class AgentProperties {

    public enum Engine {
        REACT_AGENT,
        CHAT_CLIENT
    }

    private Engine engine = Engine.REACT_AGENT;
    private double humanReviewThreshold = 0.85d;
    private int maxHistoricalCases = 3;
    private int maxPolicyRules = 3;

    public Engine getEngine() {
        return engine;
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    public double getHumanReviewThreshold() {
        return humanReviewThreshold;
    }

    public void setHumanReviewThreshold(double humanReviewThreshold) {
        if (humanReviewThreshold <= 0.0d || humanReviewThreshold > 1.0d) {
            throw new IllegalArgumentException("humanReviewThreshold must be in (0, 1]");
        }
        this.humanReviewThreshold = humanReviewThreshold;
    }

    public int getMaxHistoricalCases() {
        return maxHistoricalCases;
    }

    public void setMaxHistoricalCases(int maxHistoricalCases) {
        if (maxHistoricalCases < 0 || maxHistoricalCases > 20) {
            throw new IllegalArgumentException("maxHistoricalCases must be in [0, 20]");
        }
        this.maxHistoricalCases = maxHistoricalCases;
    }

    public int getMaxPolicyRules() {
        return maxPolicyRules;
    }

    public void setMaxPolicyRules(int maxPolicyRules) {
        if (maxPolicyRules < 0 || maxPolicyRules > 20) {
            throw new IllegalArgumentException("maxPolicyRules must be in [0, 20]");
        }
        this.maxPolicyRules = maxPolicyRules;
    }
}
