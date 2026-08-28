package com.example.alertagent.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "alert-agent.async")
public class AsyncAnalysisProperties {

    @Min(1)
    private int maxInFlight = 64;

    @NotNull
    private Duration lease = Duration.ofSeconds(90);

    @Min(1)
    private int maxAttempts = 5;

    @NotNull
    private Duration baseRetryDelay = Duration.ofSeconds(2);

    @NotNull
    private Duration maxRetryDelay = Duration.ofMinutes(5);

    @Min(1)
    private int recoveryBatchSize = 200;

    @Min(1)
    private int optimisticRetries = 5;

    public int getMaxInFlight() {
        return maxInFlight;
    }

    public void setMaxInFlight(int maxInFlight) {
        this.maxInFlight = maxInFlight;
    }

    public Duration getLease() {
        return lease;
    }

    public void setLease(Duration lease) {
        this.lease = lease;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getBaseRetryDelay() {
        return baseRetryDelay;
    }

    public void setBaseRetryDelay(Duration baseRetryDelay) {
        this.baseRetryDelay = baseRetryDelay;
    }

    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
    }

    public int getRecoveryBatchSize() {
        return recoveryBatchSize;
    }

    public void setRecoveryBatchSize(int recoveryBatchSize) {
        this.recoveryBatchSize = recoveryBatchSize;
    }

    public int getOptimisticRetries() {
        return optimisticRetries;
    }

    public void setOptimisticRetries(int optimisticRetries) {
        this.optimisticRetries = optimisticRetries;
    }
}
