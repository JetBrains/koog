package com.example;

import kotlin.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    // TODO: Make it work without requiring Kotlin Clock
    @Bean
    kotlin.time.Clock kotlinClock() {
        return Clock.System.System.INSTANCE;
    }

    public static void main(String[] args) {
        new SpringApplication(Application.class).run(args);
    }
}
