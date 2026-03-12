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
    void testAddClientReturnsRoutingPromptExecutorBuilder() {
        RoutingPromptExecutorBuilder builder = PromptExecutor.routingExecutorBuilder()
            .addClient(clientFor(providerA));

        assertThat(builder)
            .isNotNull()
            .isInstanceOf(RoutingPromptExecutorBuilder.class);
    }

    @Test
    void testSingleClientProducesRoutingLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.routingExecutorBuilder()
            .addClient(clientFor(providerA))
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testMultipleDistinctProvidersProducesRoutingLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.routingExecutorBuilder()
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerB))
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testMultipleClientsPerProviderAllowed() {
        PromptExecutor executor = PromptExecutor.routingExecutorBuilder()
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerA))
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testFallbackRegisteredProviderSucceeds() {
        LLModel fallbackModel = mock(LLModel.class);
        when(fallbackModel.getProvider()).thenReturn(providerA);

        PromptExecutor executor = PromptExecutor.routingExecutorBuilder()
            .addClient(clientFor(providerA))
            .fallback(fallbackModel)
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testFallbackUnregisteredProviderThrows() {
        LLModel fallbackModel = mock(LLModel.class);
        when(fallbackModel.getProvider()).thenReturn(providerB);

        assertThatIllegalArgumentException()
            .isThrownBy(() ->
                PromptExecutor.routingExecutorBuilder()
                    .addClient(clientFor(providerA))
                    .fallback(fallbackModel)
                    .build()
            )
            .withMessageContaining("not registered");
    }
}
