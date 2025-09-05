package com.example;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
record ChatController(
    @NonNull AIService aiService
) {

    @PostMapping("/chat")
    public Mono<ResponseEntity<ChatResponse>> chat(@RequestBody ChatRequest request) {
        return aiService.generateResponse(request.message())
            .map(
                response -> ResponseEntity.ok(new ChatResponse(response))
            )
            .onErrorReturn(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ChatResponse("Error processing request"))
            );
    }

    public record ChatRequest(String message) {
    }

    public record ChatResponse(String response) {
    }
}
