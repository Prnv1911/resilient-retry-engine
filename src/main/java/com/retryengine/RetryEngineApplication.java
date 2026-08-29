package com.retryengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activates Spring's scheduling subsystem.
// Without this, all @Scheduled annotations are silently ignored — a common gotcha.
@SpringBootApplication
@EnableScheduling
public class RetryEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetryEngineApplication.class, args);
    }
}
