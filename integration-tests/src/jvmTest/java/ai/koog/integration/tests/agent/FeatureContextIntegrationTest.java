package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.Models;
import ai.koog.integration.tests.utils.NumberTools;
import ai.koog.prompt.llm.LLModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class FeatureContextIntegrationTest extends KoogJavaTestBase {

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldExecuteMultipleHandlersInOrder(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicBoolean firstHandlerExecuted = new AtomicBoolean(false);
        AtomicBoolean secondHandlerExecuted = new AtomicBoolean(false);
        AtomicBoolean contextProvided = new AtomicBoolean(false);

        AtomicInteger executionOrder = new AtomicInteger(0);
        AtomicInteger firstHandlerOrder = new AtomicInteger(-1);
        AtomicInteger secondHandlerOrder = new AtomicInteger(-1);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> {
                    firstHandlerExecuted.set(true);
                    firstHandlerOrder.set(executionOrder.getAndIncrement());
                });
            })
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> {
                    // Check that context is provided
                    if (ctx != null) {
                        contextProvided.set(true);
                    }
                    secondHandlerExecuted.set(true);
                    secondHandlerOrder.set(executionOrder.getAndIncrement());
                });
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));

        assertThat(contextProvided).isTrue();
        assertThat(firstHandlerExecuted).isTrue();
        assertThat(secondHandlerExecuted).isTrue();

        assertThat(firstHandlerOrder.get()).isLessThan(secondHandlerOrder.get());
        assertThat(firstHandlerOrder.get()).isEqualTo(0);
        assertThat(secondHandlerOrder.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldSupportMultipleHandlersCoordinationAndPersistence(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger firstHandlerCalls = new AtomicInteger(0);
        AtomicInteger secondHandlerCalls = new AtomicInteger(0);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> firstHandlerCalls.incrementAndGet());
                config.onAgentCompleted(ctx -> firstHandlerCalls.incrementAndGet());
            })
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> secondHandlerCalls.incrementAndGet());
                config.onAgentCompleted(ctx -> secondHandlerCalls.incrementAndGet());
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));

        assertThat(firstHandlerCalls.get()).isEqualTo(2);
        assertThat(secondHandlerCalls.get()).isEqualTo(2);

        runBlocking(continuation -> agent.run("Hello", null, continuation));

        assertThat(firstHandlerCalls.get()).isEqualTo(4);
        assertThat(secondHandlerCalls.get()).isEqualTo(4);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldConfigureMultipleEvents(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger agentStartCount = new AtomicInteger(0);
        AtomicInteger llmCallCount = new AtomicInteger(0);
        AtomicInteger agentCompleteCount = new AtomicInteger(0);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> agentStartCount.incrementAndGet());
                config.onLLMCallStarting(ctx -> llmCallCount.incrementAndGet());
                config.onAgentCompleted(ctx -> agentCompleteCount.incrementAndGet());
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));

        assertThat(agentStartCount.get()).isEqualTo(1);
        assertThat(llmCallCount.get()).isGreaterThan(0);
        assertThat(agentCompleteCount.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldTriggerToolEventsInOrder_whenToolsAreUsed(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        List<String> eventOrder = new ArrayList<>();
        NumberTools calculator = new NumberTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculator).build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are an assistant with calculator tools. IMPORTANT: " +
                "You do NOT have access to random number generation - you MUST use the generateRandomNumber tool. " +
                "You MUST use the add tool for any addition operations. " +
                "You cannot perform these operations yourself. ALWAYS use the provided tools.")
            .toolRegistry(toolRegistry)
            .install(EventHandler.Feature, config -> {
                config.onAgentStarting(ctx -> eventOrder.add("AgentStarting"));
                config.onLLMCallStarting(ctx -> eventOrder.add("LLMCallStarting"));
                config.onToolCallStarting(ctx -> {
                    eventOrder.add("ToolCallStarting:" + ctx.getToolName());
                });
                config.onToolCallCompleted(ctx -> {
                    eventOrder.add("ToolCallCompleted:" + ctx.getToolName());
                });
                config.onLLMCallCompleted(ctx -> eventOrder.add("LLMCallCompleted"));
                config.onAgentCompleted(ctx -> eventOrder.add("AgentCompleted"));
            })
            .build();

        String result = runBlocking(continuation -> agent.run(
            "Generate a random number, then add 5 to it. You must use the tools.",
            null,
            continuation
        ));

        assertThat(result).isNotNull();
        assertThat(eventOrder).isNotEmpty();

        assertThat(eventOrder.get(0)).isEqualTo("AgentStarting");
        assertThat(eventOrder.get(eventOrder.size() - 1)).isEqualTo("AgentCompleted");

        boolean hasToolCallStarting = eventOrder.stream()
            .anyMatch(e -> e.startsWith("ToolCallStarting:"));
        boolean hasToolCallCompleted = eventOrder.stream()
            .anyMatch(e -> e.startsWith("ToolCallCompleted:"));

        assertThat(hasToolCallStarting).as("Tool call starting events should be triggered").isTrue();
        assertThat(hasToolCallCompleted).as("Tool call completed events should be triggered").isTrue();

        boolean hasGenerateRandomNumber = eventOrder.stream()
            .anyMatch(e -> e.contains("generateRandomNumber"));

        assertThat(hasGenerateRandomNumber)
            .as("generateRandomNumber tool must be called (LLM cannot generate random numbers)")
            .isTrue();
    }


}
