package com.example.alertagent.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncAnalysisConfigurationTest {

    @Test
    void executorRunsEachTaskOnAVirtualThread() throws Exception {
        AsyncAnalysisConfiguration configuration = new AsyncAnalysisConfiguration();

        try (ExecutorService executor = configuration.alertAnalysisVirtualThreadExecutor()) {
            Future<Boolean> virtual = executor.submit(() -> Thread.currentThread().isVirtual());
            Future<String> name = executor.submit(() -> Thread.currentThread().getName());

            assertThat(virtual.get()).isTrue();
            assertThat(name.get()).startsWith("alert-analysis-");
        }
    }
}
