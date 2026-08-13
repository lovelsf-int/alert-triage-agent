package com.example.alertagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlertTriageAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertTriageAgentApplication.class, args);
    }
}
