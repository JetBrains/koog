package ai.koog.integration.tests.agent;

import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaInteropUtils;
import ai.koog.integration.tests.utils.TestCredentials;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LLM clients (OpenAI, Anthropic, MultiLLMPromptExecutor).
 */
public class JavaLLMClientIntegrationTest extends KoogJavaTestBase {

    private void assertValidResponse(List<Message.Response> responses) {
        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertInstanceOf(Message.Assistant.class, responses.get(0));
        String content = responses.get(0).getContent();
        assertFalse(content.isEmpty());
    }

    @Test
    public void integration_OpenAILLMClient() {
        OpenAILLMClient client = new OpenAILLMClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
        resourcesToClose.add((AutoCloseable) client);

        assertEquals(LLMProvider.OpenAI, client.llmProvider());

        Prompt prompt = Prompt.builder("test-openai")
            .system("You are a helpful assistant.")
            .user("Say 'Hello from OpenAI'")
            .build();

        List<Message.Response> responses = JavaInteropUtils.executeClientBlocking(client, prompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());

        assertValidResponse(responses);
    }

    @Test
    public void integration_AnthropicLLMClient() {
        AnthropicLLMClient client = new AnthropicLLMClient(TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv());
        resourcesToClose.add((AutoCloseable) client);

        assertEquals(LLMProvider.Anthropic, client.llmProvider());

        Prompt prompt = Prompt.builder("test-anthropic")
            .system("You are a helpful assistant.")
            .user("Say 'Hello from Anthropic'")
            .build();

        List<Message.Response> responses = JavaInteropUtils.executeClientBlocking(client, prompt, AnthropicModels.Haiku_4_5, Collections.emptyList());

        assertValidResponse(responses);
    }

    @Test
    public void integration_MultiLLMPromptExecutor() {
        OpenAILLMClient openAIClient = new OpenAILLMClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
        AnthropicLLMClient anthropicClient = new AnthropicLLMClient(TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv());

        resourcesToClose.add((AutoCloseable) openAIClient);
        resourcesToClose.add((AutoCloseable) anthropicClient);

        MultiLLMPromptExecutor executor = new MultiLLMPromptExecutor(
            Map.of(
                LLMProvider.OpenAI, openAIClient,
                LLMProvider.Anthropic, anthropicClient
            )
        );

        Prompt openAIPrompt = Prompt.builder("test-multi-openai")
            .system("You are a helpful assistant.")
            .user("Say 'OpenAI response'")
            .build();

        List<Message.Response> openAIResponses = JavaInteropUtils.executeExecutorBlocking(executor, openAIPrompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());
        assertValidResponse(openAIResponses);

        Prompt anthropicPrompt = Prompt.builder("test-multi-anthropic")
            .system("You are a helpful assistant.")
            .user("Say 'Anthropic response'")
            .build();

        List<Message.Response> anthropicResponses = JavaInteropUtils.executeExecutorBlocking(executor, anthropicPrompt, AnthropicModels.Haiku_4_5, Collections.emptyList());
        assertValidResponse(anthropicResponses);
    }
}
