package ai.koog.prompt.executor.model;

import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.llms.MockLLMClient;
import ai.koog.prompt.executor.llms.RoutingLLMPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingPromptExecutorBuilderTest {

    private final LLMProvider providerA = mock(LLMProvider.class);
    private final LLMProvider providerB = mock(LLMProvider.class);

    private LLMClient clientFor(LLMProvider provider) {
        return MockLLMClient.simpleClientMock(provider, "response");
    }

    @Test
    void testBuilderAccessibleFromPromptExecutorCompanion() {
        assertThat(PromptExecutor.routingExecutorBuilder())
            .isNotNull()
            .isInstanceOf(RoutingPromptExecutorBuilder.class);
    }

    @Test
    void testBuildWithSingleClientProducesRoutingLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.routingExecutorBuilder()
            .addClient(clientFor(providerA))
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testMultipleClientsPerProviderAllowed() {
        // Routing allows many clients for the same provider — they are load-balanced
        PromptExecutor executor = PromptExecutor.routingExecutorBuilder()
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerA))
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testBuildWithNoClientsThrows() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> PromptExecutor.routingExecutorBuilder().build())
            .withMessageContaining("At least one LLM client must be added");
    }

    @Test
    void testWithFallbackRegisteredProviderSucceeds() {
        LLModel fallbackModel = mock(LLModel.class);
        when(fallbackModel.getProvider()).thenReturn(providerA);

        PromptExecutor executor = PromptExecutor.routingExecutorBuilder()
            .addClient(clientFor(providerA))
            .withFallback(fallbackModel)
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testWithFallbackUnregisteredProviderThrows() {
        LLModel fallbackModel = mock(LLModel.class);
        when(fallbackModel.getProvider()).thenReturn(providerB);

        assertThatIllegalArgumentException()
            .isThrownBy(() ->
                PromptExecutor.routingExecutorBuilder()
                    .addClient(clientFor(providerA))
                    .withFallback(fallbackModel)
                    .build()
            )
            .withMessageContaining("not registered");
    }
}
