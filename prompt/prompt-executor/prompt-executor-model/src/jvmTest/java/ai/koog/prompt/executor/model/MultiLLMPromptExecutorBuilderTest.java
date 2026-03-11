package ai.koog.prompt.executor.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.llms.MockLLMClient;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MultiLLMPromptExecutorBuilderTest {

    private final LLMProvider providerA = mock(LLMProvider.class);
    private final LLMProvider providerB = mock(LLMProvider.class);

    private LLMClient clientFor(LLMProvider provider) {
        return MockLLMClient.simpleClientMock(provider, "response");
    }

    private static Stream<MultiLLMPromptExecutorBuilder> multiBuilders() {
        return Stream.of(
            PromptExecutor.builder(),
            PromptExecutor.multiExecutorBuilder()
        );
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testBuildWithSingleClientProducesMultiLLMPromptExecutor(MultiLLMPromptExecutorBuilder builder) {
        PromptExecutor executor = builder
            .addClient(clientFor(providerA))
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testBuildWithMultipleClientsProducesMultiLLMPromptExecutor(MultiLLMPromptExecutorBuilder builder) {
        PromptExecutor executor = builder
            .addClient(clientFor(providerA))
            .addClient(clientFor(providerB))
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testBuildWithNoClientsThrows(MultiLLMPromptExecutorBuilder builder) {
        assertThatIllegalArgumentException()
            .isThrownBy(builder::build)
            .withMessageContaining("At least one LLM client must be added");
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testLastWinsForDuplicateProvider(MultiLLMPromptExecutorBuilder builder) {
        LLMClient first = clientFor(providerA);
        LLMClient second = clientFor(providerA);

        // Should not throw — last registration for providerA wins
        PromptExecutor executor = builder
            .addClient(first)
            .addClient(second)
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testWithFallbackRegisteredProviderSucceeds(MultiLLMPromptExecutorBuilder builder) {
        LLModel fallbackModel = mock(LLModel.class);
        when(fallbackModel.getProvider()).thenReturn(providerA);

        PromptExecutor executor = builder
            .addClient(clientFor(providerA))
            .withFallback(fallbackModel)
            .build();

        assertThat(executor).isInstanceOf(MultiLLMPromptExecutor.class);
    }

    @ParameterizedTest
    @MethodSource("multiBuilders")
    void testWithFallbackUnregisteredProviderThrows(MultiLLMPromptExecutorBuilder builder) {
        LLModel fallbackModel = mock(LLModel.class);
        when(fallbackModel.getProvider()).thenReturn(providerB);

        assertThatIllegalArgumentException()
            .isThrownBy(() ->
                builder
                    .addClient(clientFor(providerA))
                    .withFallback(fallbackModel)
                    .build()
            )
            .withMessageContaining("not registered");
    }
}
