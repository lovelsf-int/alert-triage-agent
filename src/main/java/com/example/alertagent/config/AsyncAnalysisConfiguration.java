package com.example.alertagent.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AsyncAnalysisConfiguration {

    @Bean(name = "alertAnalysisVirtualThreadExecutor", destroyMethod = "close")
    public ExecutorService alertAnalysisVirtualThreadExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("alert-analysis-", 0)
                .factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }

    @Bean
    @Qualifier("alertAnalysisClock")
    public Clock alertAnalysisClock() {
        return Clock.systemUTC();
    }
}
