package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.AIAgentSimpleStrategiesKt;
import ai.koog.agents.core.agent.ToolCalls;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.NumberTools;
import ai.koog.integration.tests.utils.Models;
import ai.koog.integration.tests.utils.SubgraphStrategies;
import ai.koog.prompt.llm.LLModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RunModesIntegrationTest extends KoogJavaTestBase {

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldExecuteSequentialStrategy_whenUsingSingleRunStrategy(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger toolCallCount = new AtomicInteger(0);
        AtomicBoolean agentCompleted = new AtomicBoolean(false);

        NumberTools calculator = new NumberTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculator).build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. You MUST use the add tool when needed. DO NOT answer without calling tools.")
            .toolRegistry(toolRegistry)
            .graphStrategy(AIAgentSimpleStrategiesKt.singleRunStrategy())
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(ctx -> toolCallCount.incrementAndGet());
                config.onAgentCompleted(ctx -> agentCompleted.set(true));
            })
            .build();

        String result = runBlocking(continuation -> agent.run("Calculate 10 + 5", null, continuation));

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(agentCompleted.get()).isTrue();
        assertThat(toolCallCount.get()).isGreaterThanOrEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldExecuteParallelStrategy_whenUsingParallelMode(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        NumberTools calculator = new NumberTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculator).build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. You MUST use tools when needed. DO NOT answer without calling tools.")
            .toolRegistry(toolRegistry)
            .graphStrategy(AIAgentSimpleStrategiesKt.singleRunStrategy(ToolCalls.PARALLEL))
            .build();

        String result = runBlocking(continuation -> agent.run("What is 7 + 3?", null, continuation));

        assertThat(result).isNotNull().isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldExecuteSingleRunMode_whenUsingSingleRunSequential(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger toolCallCount = new AtomicInteger(0);

        NumberTools calculator = new NumberTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculator).build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. You MUST use tools one at a time. DO NOT answer without calling tools.")
            .toolRegistry(toolRegistry)
            .graphStrategy(AIAgentSimpleStrategiesKt.singleRunStrategy(ToolCalls.SINGLE_RUN_SEQUENTIAL))
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(ctx -> toolCallCount.incrementAndGet());
            })
            .build();

        String result = runBlocking(continuation -> agent.run("Add 4 and 6", null, continuation));

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(toolCallCount.get()).isGreaterThanOrEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldUseDefaultSequentialMode_whenNoModeSpecified(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger toolCallCount = new AtomicInteger(0);
        AtomicBoolean agentCompleted = new AtomicBoolean(false);

        NumberTools calculator = new NumberTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculator).build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator. You MUST use the add tool. DO NOT answer without calling tools.")
            .toolRegistry(toolRegistry)
            .graphStrategy(AIAgentSimpleStrategiesKt.singleRunStrategy())
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(ctx -> toolCallCount.incrementAndGet());
                config.onAgentCompleted(ctx -> agentCompleted.set(true));
            })
            .build();

        String result = runBlocking(continuation -> agent.run("What is 3 + 7?", null, continuation));

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(agentCompleted.get()).isTrue();
        assertThat(toolCallCount.get()).isGreaterThanOrEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldExecuteComplexWorkflow_whenUsingStrategyWithSubgraphs(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger toolCallCount = new AtomicInteger(0);
        AtomicBoolean agentCompleted = new AtomicBoolean(false);

        NumberTools calculator = new NumberTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculator).build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator assistant. You MUST use tools to solve problems. DO NOT answer without calling tools.")
            .maxIterations(20)
            .toolRegistry(toolRegistry)
            .graphStrategy(SubgraphStrategies.calculatorWithSubgraphs(model))
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(ctx -> toolCallCount.incrementAndGet());
                config.onAgentCompleted(ctx -> agentCompleted.set(true));
            })
            .build();

        String result = runBlocking(continuation -> agent.run("What is 15 + 25?", null, continuation));

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(agentCompleted.get()).isTrue();
        assertThat(toolCallCount.get()).isGreaterThanOrEqualTo(1);
    }
}
