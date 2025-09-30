package com.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = "test")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class ChatControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldHandleAiRequest() {
        final var result = webTestClient.post()
            .uri("/api/chat")
            .bodyValue(new ChatController.ChatRequest("Say Hello"))
            .exchange()
            .expectStatus().isOk()
            .expectBody(ChatController.ChatResponse.class)
            .returnResult();

        final var responseBody = result.getResponseBody();
        Assertions.assertNotNull(responseBody);
        assertThat(responseBody.response()).containsIgnoringCase("Hello");
    }

}
