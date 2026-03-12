package ai.koog.prompt.executor.model;

import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.llms.MockLLMClient;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiLLMPromptExecutorBuilderTest {

    private final LLMProvider providerA = mock(LLMProvider.class);
    private final LLMProvider providerB = mock(LLMProvider.class);

    private LLMClient clientFor(LLMProvider provider) {
        return MockLLMClient.simpleClientMock(provider, "response");
    }

    private static Stream<InitialMultiLLMPromptExecutorBuilder> multiBuilders() {
        return Stream.of(
            PromptExecutor.builder(),
            PromptExecutor.multiExecutorBuilder()
        );
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testAddClientReturnsMultiLLMPromptExecutorBuilder(InitialMultiLLMPromptExecutorBuilder builder) {
        assertThat(builder.addClient(clientFor(providerA)))
            .isNotNull()
            .isInstanceOf(MultiLLMPromptExecutorBuilder.class);
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testSingleClientProducesMultiLLMPromptExecutor(InitialMultiLLMPromptExecutorBuilder builder) {
        PromptExecutor executor = builder
            .addClient(clientFor(providerA))
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testMultipleDistinctProvidersProducesMultiLLMPromptExecutor(InitialMultiLLMPromptExecutorBuilder builder) {
        PromptExecutor executor = builder
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerB))
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testDuplicateProviderLastWins(InitialMultiLLMPromptExecutorBuilder builder) {
        // Duplicate providers are allowed — the last registered client for a provider wins
        PromptExecutor executor = builder
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerA))
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testFallbackRegisteredProviderSucceeds(InitialMultiLLMPromptExecutorBuilder builder) {
        LLModel fallbackModel = mock(LLModel.class);
        when(fallbackModel.getProvider()).thenReturn(providerA);

        PromptExecutor executor = builder
            .addClient(clientFor(providerA))
            .fallback(fallbackModel)
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testFallbackUnregisteredProviderThrows(InitialMultiLLMPromptExecutorBuilder builder) {
        LLModel fallbackModel = mock(LLModel.class);
        when(fallbackModel.getProvider()).thenReturn(providerB);

        assertThatIllegalArgumentException()
            .isThrownBy(() ->
                builder
                    .addClient(clientFor(providerA))
                    .fallback(fallbackModel)
                    .build()
            )
            .withMessageContaining("not registered");
    }
}
