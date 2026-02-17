package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.AIAgentSimpleStrategiesKt;
import ai.koog.agents.core.agent.ToolCalls;
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.prompt.llm.LLMCapability;
import ai.koog.prompt.llm.LLModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.koog.agents.core.agent.AIAgentSimpleStrategiesKt.singleRunStrategy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for advanced AIAgent features (state, result, custom pipelines).
 */
public class JavaAIAgentAdvancedFeaturesIntegrationTest extends KoogJavaTestBase {

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_CustomPipelineFeature(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger llmInterceptCount = new AtomicInteger(0);
        AtomicInteger toolInterceptCount = new AtomicInteger(0);
        AtomicBoolean agentStarted = new AtomicBoolean(false);
        AtomicBoolean agentCompleted = new AtomicBoolean(false);

        TransactionTools transactionTools = new TransactionTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(transactionTools).build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant. When asked for transaction IDs, you MUST ALWAYS call the getTransactionId tool. " +
                "You do NOT know transaction IDs - you MUST call the tool to get them. NEVER make up transaction IDs. " +
                "ALWAYS use the tool. NO EXCEPTIONS.")
            .toolRegistry(toolRegistry)
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(context -> agentStarted.set(true));
                config.onAgentCompleted(context -> agentCompleted.set(true));
                config.onLLMCallStarting(context -> llmInterceptCount.incrementAndGet());
                config.onToolCallStarting(context -> toolInterceptCount.incrementAndGet());
            })
            .build();

        String result = runBlocking(continuation -> agent.run("What is the transaction ID for order number 12345? You must use the getTransactionId tool.", null, continuation));

        assertNotNull(result);
        assertTrue(agentStarted.get(), "Agent should have started");
        assertTrue(agentCompleted.get(), "Agent should have completed");
        assertTrue(llmInterceptCount.get() > 0, "LLM interceptor should have been called");
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_MultipleHandlersForToolCallEvents(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        Assumptions.assumeTrue(
            model.supports(LLMCapability.Tools.INSTANCE),
            "Model " + model + " does not support tools"
        );

        List<String> toolStartingCallOrder = new ArrayList<>();

        CalculatorTools calculatorTools = new CalculatorTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculatorTools).build();

        AIAgent<String, String> agent = AIAgent.builder()
            .graphStrategy(singleRunStrategy(ToolCalls.SEQUENTIAL))
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant. JUST CALL THE TOOLS, NO QUESTIONS ASKED.")
            .toolRegistry(toolRegistry)
            .maxIterations(10)
            .install(EventHandler.Feature, eventConfig -> {
                eventConfig.onToolCallStarting(context -> {
                    toolStartingCallOrder.add("Tool Starting Handler 1: " + context.getToolName());
                });
                eventConfig.onToolCallStarting(context -> {
                    toolStartingCallOrder.add("Tool Starting Handler 2: " + context.getToolName());
                });
                eventConfig.onToolCallStarting(context -> {
                    toolStartingCallOrder.add("Tool Starting Handler 3: " + context.getToolName());
                });
            })
            .build();

        String result = runBlocking(continuation -> agent.run("Calculate 7 times 8", null, continuation));

        assertNotNull(result, "Result should not be null");
        assertEquals(3, toolStartingCallOrder.size(), "Three starting handlers should be called");
        assertTrue(toolStartingCallOrder.get(0).contains("Tool Starting Handler 1"), "First handler should be Handler 1");
        assertTrue(toolStartingCallOrder.get(1).contains("Tool Starting Handler 2"), "Second handler should be Handler 2");
        assertTrue(toolStartingCallOrder.get(2).contains("Tool Starting Handler 3"), "Third handler should be Handler 3");
    }
}
