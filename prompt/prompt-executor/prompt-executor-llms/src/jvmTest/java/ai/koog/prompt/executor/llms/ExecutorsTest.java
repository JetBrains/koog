package ai.koog.prompt.executor.llms;

import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.model.JavaPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.params.LLMParams;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecutorsTest {

    @Mock(strictness = Mock.Strictness.LENIENT)
    LLModel model;

    LLMProvider provider = mock(LLMProvider.class);

    LLMClient llmClient = new MockOpenAILLMClient("Hello from LLM");
    LLMClient failingClient = new MockOpenAILLMClient("Hello from LLM", true);

    Iterable<JavaPromptExecutor> promptExecutors() {
        return List.of(
            Executors.promptExecutor(provider, llmClient),
            Executors.promptExecutor(Map.of(provider, llmClient)),
            Executors.promptExecutor(llmClient)
        );
    }

    Iterable<JavaPromptExecutor> failingPromptExecutors() {
        return List.of(
            Executors.promptExecutor(provider, failingClient),
            Executors.promptExecutor(Map.of(provider, failingClient)),
            Executors.promptExecutor(failingClient)
        );
    }

    @ParameterizedTest
    @MethodSource("promptExecutors")
    void shouldExecutePromptAsync(JavaPromptExecutor promptExecutor) {
        when(model.getProvider()).thenReturn(provider);
        // given
        assertThat(promptExecutor).isNotNull();

        final var requestMeta = RequestMetaInfo.Companion.getEmpty();

        final var systemMessage = new Message.System(
            "You are helpful assistant", requestMeta);

        final var userMessage = new Message.User(
            "Say Hello", requestMeta);

        final Prompt prompt = new Prompt(
            List.of(systemMessage, userMessage),
            UUID.randomUUID().toString(),
            new LLMParams()
        );

        // when
        final var future = promptExecutor.executeAsync(prompt, model);

        // then
        assertThat(future)
            .succeedsWithin(Duration.ofSeconds(3))
            .satisfies(responses -> {
                    assertThat(responses)
                        .hasSize(1)
                        .first().satisfies(assistantResponse -> {
                            assertThat(assistantResponse)
                                .isNotNull()
                                .isInstanceOf(Message.Assistant.class);
                            assertThat(assistantResponse.getRole()).isEqualTo(Message.Role.Assistant);
                            assertThat(assistantResponse.getContent()).isEqualTo("Hello from LLM");
                        });
                }
            );
    }

    @ParameterizedTest
    @MethodSource("failingPromptExecutors")
    void shouldExecutePromptAsyncWithError(JavaPromptExecutor promptExecutor) {
        when(model.getProvider()).thenReturn(provider);
        // given
        assertThat(promptExecutor).isNotNull();

        final var requestMeta = RequestMetaInfo.Companion.getEmpty();

        final var systemMessage = new Message.System(
            "You are helpful assistant", requestMeta);

        final var userMessage = new Message.User(
            "Say Hello", requestMeta);

        final Prompt prompt = new Prompt(
            List.of(systemMessage, userMessage),
            UUID.randomUUID().toString(),
            new LLMParams()
        );

        // when
        final var future = promptExecutor.executeAsync(prompt, model);

        // then
        assertThat(future)
            .completesExceptionallyWithin(Duration.ofSeconds(3))
            .withThrowableThat().satisfies(throwable -> {
                assertThat(throwable).isInstanceOf(ExecutionException.class);
                assertThat(throwable).hasRootCauseMessage("Throw exception for test");
            });
    }
}
