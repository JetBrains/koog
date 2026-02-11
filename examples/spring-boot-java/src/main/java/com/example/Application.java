package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    // TODO: Make it work without requiring Kotlin Clock
    @Bean
    kotlinx.datetime.Clock kotlinClock() {
        return kotlinx.datetime.Clock.System.INSTANCE;
    }

    public static void main(String[] args) {
        new SpringApplication(Application.class).run(args);
    }
}
