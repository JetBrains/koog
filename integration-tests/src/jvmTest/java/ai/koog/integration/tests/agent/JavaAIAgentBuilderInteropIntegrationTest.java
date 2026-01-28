package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaInteropUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.integration.tests.utils.TestCredentials;
import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AIAgent builder with basic configuration, tools, and event handlers.
 */
public class JavaAIAgentBuilderInteropIntegrationTest extends KoogJavaTestBase {

    private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

    @AfterEach
    public void cleanup() throws Exception {
        for (AutoCloseable resource : resourcesToClose) {
            resource.close();
        }
        resourcesToClose.clear();
    }

    private MultiLLMPromptExecutor createExecutor(LLModel model) {
        LLMClient client;
        if (model.getProvider() == LLMProvider.OpenAI.INSTANCE) {
            client = JavaInteropUtils.createOpenAIClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
        } else if (model.getProvider() == LLMProvider.Anthropic.INSTANCE) {
            client = JavaInteropUtils.createAnthropicClient(TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv());
        } else {
            throw new IllegalArgumentException("Unsupported provider: " + model.getProvider());
        }
        resourcesToClose.add((AutoCloseable) client);
        return new MultiLLMPromptExecutor(client);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_BuilderBasicUsageAndTemperature(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        MultiLLMPromptExecutor executor = createExecutor(model);

        // Test basic builder usage
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        String result = runBlocking(continuation ->
            agent.run("What is the capital of France?", continuation)
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("Paris"));

        // Test builder with temperature setting
        AIAgent<String, String> agentWithTemp = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .temperature(0.5)
            .build();

        String tempResult = runBlocking(continuation -> agentWithTemp.run("Say hello", continuation));

        assertNotNull(tempResult);
        assertFalse(tempResult.isEmpty());
        assertTrue(tempResult.toLowerCase().contains("hello") || tempResult.toLowerCase().contains("hi"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_BuilderWithToolRegistry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. Use the add and multiply tools as needed.")
            .toolRegistry(toolRegistry)
            .build();

        // Test with complex expression using both tools
        String result = runBlocking(continuation -> agent.run("Calculate (5 + 3) * 2", continuation));

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_EventHandler(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicBoolean agentStarted = new AtomicBoolean(false);
        AtomicBoolean agentCompleted = new AtomicBoolean(false);
        AtomicInteger llmCallsCount = new AtomicInteger(0);

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. Use the add tool when needed.")
            .toolRegistry(toolRegistry)
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> agentStarted.set(true));
                config.onAgentCompleted(ctx -> agentCompleted.set(true));
                config.onLLMCallStarting(ctx -> llmCallsCount.incrementAndGet());
            })
            .build();

        String result = runBlocking(continuation ->
            agent.run("What is 8 + 12?", continuation)
        );

        assertNotNull(result);
        assertTrue(agentStarted.get(), "Agent should have started");
        assertTrue(agentCompleted.get(), "Agent should have completed");
        assertTrue(llmCallsCount.get() > 0, "LLM should have been called at least once");
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_BuilderWithMaxIterations(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        ToolRegistry toolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant with calculator tools.")
            .toolRegistry(toolRegistry)
            .maxIterations(5)
            .build();

        String result = runBlocking(continuation -> agent.run("What is 5 + 3?", continuation));

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
