package com.example;

import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.JavaPromptExecutor;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.Message.Response;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.params.LLMParams;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
record AIService(
    @NonNull JavaPromptExecutor executor,
    kotlinx.datetime.Clock clock
) {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    Mono<String> generateResponse(String input) {

        RequestMetaInfo metaInfo = RequestMetaInfo.Companion.create(clock);
        final var systemPrompt = new Message.System("You are a helpful pirate", metaInfo);
        final var userPrompt = new Message.User(input, metaInfo);
        final var params = new LLMParams();
        final var prompt = new Prompt(
            List.of(systemPrompt, userPrompt),
            UUID.randomUUID().toString(),
            params
        );

        return Mono.fromFuture(
            executor.executeAsync(prompt, OpenAIModels.CostOptimized.INSTANCE.getGPT4_1Nano())
                .handle((responses, thowable) -> {
                        if (thowable != null) {
                            log.error("Error executing prompt", thowable);
                            return "Error: " + thowable.getMessage();
                        } else {
                            return responses.stream()
                                .map(Response::getContent)
                                .collect(Collectors.joining(", "));
                        }
                    }
                ));
    }
}
