package ai.koog.integration.tests.executor;

import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.Models;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaPromptExecutorIntegrationTest extends KoogJavaTestBase {

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldExecutePrompt(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assertThat(model.getProvider()).isNotNull();

        MultiLLMPromptExecutor executor = createExecutor(model);
        Prompt prompt = Prompt.builder("test-prompt")
            .system("You are a calculator.")
            .user("What is 2+2?")
            .assistant("Shall I answer in a number or in a word?")
            .user("In a word please")
            .build();

        assertThat(prompt.getMessages().get(0)).isInstanceOf(Message.System.class);
        assertThat(prompt.getMessages().get(1)).isInstanceOf(Message.User.class);
        assertThat(prompt.getMessages().get(2)).isInstanceOf(Message.Assistant.class);
        assertThat(prompt.getMessages().get(3)).isInstanceOf(Message.User.class);
        List<Message.Response> responses = executor.execute(prompt, model);

        Message.Response firstResponse = responses.get(0);

        assertThat(firstResponse).isInstanceOf(Message.Assistant.class);
        String content = firstResponse.getContent();
        assertThat(content.toLowerCase()).contains("four");
    }
}
