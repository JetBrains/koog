package com.example;

import ai.koog.utils.time.AgentClock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    @Bean
    AgentClock agentClock() {
        return AgentClock.Companion.getSystem();
    }

    public static void main(String[] args) {
        new SpringApplication(Application.class).run(args);
    }
}
