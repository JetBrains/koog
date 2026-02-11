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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LLM clients (OpenAI, Anthropic, MultiLLMPromptExecutor).
 */
public class JavaLLMClientIntegrationTest extends KoogJavaTestBase {

    private void assertValidResponse(List<Message.Response> responses) {
        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertInstanceOf(Message.Assistant.class, responses.get(0));
        String content = JavaInteropUtils.getAssistantContent((Message.Assistant) responses.get(0));
        assertFalse(content.isEmpty());
    }

    @Test
    public void integration_OpenAILLMClient() {
        OpenAILLMClient client = JavaInteropUtils.createOpenAIClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
        resourcesToClose.add((AutoCloseable) client);

        assertEquals(LLMProvider.OpenAI.INSTANCE, client.llmProvider());

        Prompt prompt = JavaInteropUtils.buildSimplePrompt("test-openai", "You are a helpful assistant.", "Say 'Hello from OpenAI'");
        List<Message.Response> responses = JavaInteropUtils.executeClientBlocking(client, prompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());

        assertValidResponse(responses);
    }

    @Test
    public void integration_AnthropicLLMClient() {
        AnthropicLLMClient client = JavaInteropUtils.createAnthropicClient(TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv());
        resourcesToClose.add((AutoCloseable) client);

        assertEquals(LLMProvider.Anthropic.INSTANCE, client.llmProvider());

        Prompt prompt = JavaInteropUtils.buildSimplePrompt("test-anthropic", "You are a helpful assistant.", "Say 'Hello from Anthropic'");
        List<Message.Response> responses = JavaInteropUtils.executeClientBlocking(client, prompt, AnthropicModels.Haiku_4_5, Collections.emptyList());

        assertValidResponse(responses);
    }

    @Test
    public void integration_MultiLLMPromptExecutor() {
        OpenAILLMClient openAIClient = JavaInteropUtils.createOpenAIClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
        AnthropicLLMClient anthropicClient = JavaInteropUtils.createAnthropicClient(TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv());

        resourcesToClose.add((AutoCloseable) openAIClient);
        resourcesToClose.add((AutoCloseable) anthropicClient);

        MultiLLMPromptExecutor executor = JavaInteropUtils.createMultiLLMPromptExecutor(openAIClient, anthropicClient);

        Prompt openAIPrompt = JavaInteropUtils.buildSimplePrompt("test-multi-openai", "You are a helpful assistant.", "Say 'OpenAI response'");
        List<Message.Response> openAIResponses = JavaInteropUtils.executeExecutorBlocking(executor, openAIPrompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());
        assertValidResponse(openAIResponses);

        Prompt anthropicPrompt = JavaInteropUtils.buildSimplePrompt("test-multi-anthropic", "You are a helpful assistant.", "Say 'Anthropic response'");
        List<Message.Response> anthropicResponses = JavaInteropUtils.executeExecutorBlocking(executor, anthropicPrompt, AnthropicModels.Haiku_4_5, Collections.emptyList());
        assertValidResponse(anthropicResponses);
    }
}
