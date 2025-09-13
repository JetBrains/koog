package com.example;

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor;
import ai.koog.prompt.executor.model.JavaPromptExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KoogConfiguration {

    @Bean
    JavaPromptExecutor executor(SingleLLMPromptExecutor promptExecutor) {
        return new JavaPromptExecutor(promptExecutor);
    }
}
