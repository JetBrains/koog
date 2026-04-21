package ai.koog.prompt.executor.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.llms.MockLLMClient;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.llms.RoutingLLMPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import org.junit.jupiter.api.Test;

class PromptExecutorBuilderTest {

    private final LLMProvider providerA = mock(LLMProvider.class);
    private final LLMProvider providerB = mock(LLMProvider.class);

    private LLMClient clientFor(LLMProvider provider) {
        return MockLLMClient.simpleClientMock(provider, "response");
    }

    @Test
    void testAddClientReturnsPromptExecutorBuilder() {
        assertThat(PromptExecutor.builder().addClient(clientFor(providerA)))
            .isNotNull()
            .isInstanceOf(PromptExecutorBuilder.class);
    }

    @Test
    void testSingleClientProducesMultiLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.builder()
            .addClient(clientFor(providerA))
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @Test
    void testTwoDistinctProvidersProducesMultiLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.builder()
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerB))
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @Test
    void testDuplicateProviderProducesRoutingLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.builder()
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerA))
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testMixedClientsWithDuplicateProviderProducesRoutingLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.builder()
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerB))
            .addClient(clientFor(providerA))
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testFallbackWithRegisteredProviderSucceeds() {
        LLModel fallbackModel = mock(LLModel.class);
        when(fallbackModel.getProvider()).thenReturn(providerA);

        PromptExecutor executor = PromptExecutor.builder()
            .addClient(clientFor(providerA))
            .fallback(fallbackModel)
            .build();

        assertThat(executor).isNotNull();
    }

    @Test
    void testProviderMethodProducesMultiLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.builder()
            .openAI("fake-key")
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @Test
    void testTwoCallsToSameProviderMethodProducesRoutingLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.builder()
            .openAI("fake-key-1")
            .openAI("fake-key-2")
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testMixedProviderMethodsProducesMultiLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.builder()
            .openAI("fake-key")
            .anthropic("fake-key")
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @Test
    void testMixedAndDuplicatedProviderMethodsProducesRoutingLLMPromptExecutor() {
        PromptExecutor executor = PromptExecutor.builder()
            .openAI("fake-key")
            .anthropic("fake-key")
            .openAI("fake-key")
            .build();

        assertThat(executor).isInstanceOf(RoutingLLMPromptExecutor.class);
    }

    @Test
    void testFallbackWithUnregisteredProviderThrows() {
        LLModel fallbackModel = mock(LLModel.class);
        when(fallbackModel.getProvider()).thenReturn(providerB);

        assertThatIllegalArgumentException()
            .isThrownBy(() ->
                PromptExecutor.builder()
                    .addClient(clientFor(providerA))
                    .fallback(fallbackModel)
                    .build()
            )
            .withMessageContaining("not registered");
    }
}
