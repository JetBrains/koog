package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase;
import ai.koog.agents.core.agent.entity.AIAgentEdge;
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
import ai.koog.agents.core.agent.entity.AIAgentNode;
import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
import ai.koog.agents.core.agent.entity.CompressHistoryNodeBuilder;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.agents.snapshot.feature.Persistence;
import ai.koog.agents.snapshot.feature.PersistenceKt;
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider;
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider;
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.Models;
import ai.koog.agents.ext.agent.CriticResult;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.llm.LLMCapability;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import ai.koog.serialization.TypeToken;
import kotlin.coroutines.EmptyCoroutineContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.koog.agents.core.utils.CoroutineUtilsKt.runBlockingIfRequired;
import static org.junit.jupiter.api.Assertions.*;

public class JavaAIAgentGraphStrategyTest extends KoogJavaTestBase {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithTypedNodeAndLlmNode(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        var strategy = AIAgentGraphStrategy.builder("java-typed-node-graph")
            .withInput(String.class)
            .withOutput(String.class);

        var preprocess = AIAgentNode.builder("preprocess")
            .withInput(String.class)
            .withOutput(String.class)
            .withAction((input, ctx) -> "Reply with the single word hello to: " + input)
            .build();

        var llm = AIAgentNode.llmRequest(true, "llm");
        var extractContent = AIAgentNode.builder("extract-content")
            .withInput(Message.Response.class)
            .withOutput(String.class)
            .withAction((response, ctx) -> assistantContent(response, ""))
            .build();

        strategy.edge(strategy.nodeStart, preprocess);
        strategy.edge(preprocess, llm);
        strategy.edge(llm, extractContent);
        strategy.edge(AIAgentEdge.builder()
            .from(extractContent)
            .to(strategy.nodeFinish)
            .build());

        AIAgent<String, String> agent = AIAgent.builder()
            .graphStrategy(strategy.build())
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a concise assistant.")
            .build();

        String result = agent.run("Java graph API", null);

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(containsIgnoreCase(result, "hello"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithTaskSubgraphAndLimitedTools(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        Assumptions.assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model does not support tools");

        CalculatorTools calculatorTools = new CalculatorTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculatorTools).build();
        EventRecorder events = new EventRecorder();

        var strategy = AIAgentGraphStrategy.builder("java-subgraph-limited-tools")
            .withInput(String.class)
            .withOutput(String.class);

        var calcSubgraph = AIAgentSubgraph.builder("calc-subgraph")
            .limitedTools(List.of(calculatorTools.getTool("multiply")))
            .withInput(String.class)
            .withOutput(String.class)
            .withTask(input -> "Use the multiply tool to calculate 7 * 8. Return only the numeric answer.")
            .build();

        strategy.edge(strategy.nodeStart, calcSubgraph);
        strategy.edge(calcSubgraph, strategy.nodeFinish);

        AIAgent<String, String> agent = AIAgent.builder()
            .graphStrategy(strategy.build())
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator assistant. Use only the provided calculator tools when calculation is required.")
            .toolRegistry(toolRegistry)
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(context -> events.toolNames.add(context.getToolName()));
                config.onSubgraphExecutionStarting(context -> events.subgraphNames.add(context.getSubgraph().getName()));
            })
            .build();

        String result = agent.run("Calculate 7 times 8", null);

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.contains("56"), "Result should contain the multiplication result");
        assertTrue(events.toolNames.contains("multiply"));
        assertTrue(events.subgraphNames.contains("calc-subgraph"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyShouldEmitStrategyNodeSubgraphAndToolEvents(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        Assumptions.assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model does not support tools");

        CalculatorTools calculatorTools = new CalculatorTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculatorTools).build();
        EventRecorder events = new EventRecorder();

        var strategy = AIAgentGraphStrategy.builder("java-graph-events")
            .withInput(String.class)
            .withOutput(String.class);

        var prepare = AIAgentNode.builder("prepare")
            .withInput(String.class)
            .withOutput(String.class)
            .withAction((input, ctx) -> "Calculate 6 * 9. Return only the result.")
            .build();

        var calcSubgraph = AIAgentSubgraph.builder("tool-subgraph")
            .limitedTools(List.of(calculatorTools.getTool("multiply")))
            .withInput(String.class)
            .withOutput(String.class)
            .withTask(input -> input)
            .build();

        strategy.edge(strategy.nodeStart, prepare);
        strategy.edge(prepare, calcSubgraph);
        strategy.edge(calcSubgraph, strategy.nodeFinish);

        AIAgent<String, String> agent = AIAgent.builder()
            .graphStrategy(strategy.build())
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator assistant. Use the multiply tool.")
            .toolRegistry(toolRegistry)
            .install(EventHandler.Feature, config -> {
                config.onStrategyStarting(context -> events.strategyStarted.incrementAndGet());
                config.onStrategyCompleted(context -> events.strategyCompleted.incrementAndGet());
                config.onNodeExecutionStarting(context -> events.nodeNames.add(context.getNode().getName()));
                config.onSubgraphExecutionStarting(context -> events.subgraphNames.add(context.getSubgraph().getName()));
                config.onSubgraphExecutionCompleted(context -> events.completedSubgraphNames.add(context.getSubgraph().getName()));
                config.onToolCallStarting(context -> events.toolNames.add(context.getToolName()));
            })
            .build();

        String result = agent.run("event run", null);

        assertNotNull(result);
        assertEquals(1, events.strategyStarted.get());
        assertEquals(1, events.strategyCompleted.get());
        assertFalse(events.nodeNames.isEmpty(), "At least one node should have executed");
        assertTrue(result.contains("54"), "Result should contain the multiplication result");
        assertTrue(events.subgraphNames.contains("tool-subgraph"));
        assertTrue(events.completedSubgraphNames.contains("tool-subgraph"));
        assertTrue(events.nodeNames.contains("prepare"));
        assertTrue(events.toolNames.contains("multiply"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithVerificationPath(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        var strategy = AIAgentGraphStrategy.builder("java-graph-verification")
            .withInput(String.class)
            .withOutput(Boolean.class);

        var verification = AIAgentSubgraph.builder("verification-subgraph")
            .withInput(String.class)
            .withVerification(input -> "Answer true only if the statement is factually correct: " + input)
            .build();
        var verificationResult = AIAgentNode.builder("verification-result")
            .<CriticResult<String>>withInput(TypeToken.of(CriticResult.class, List.of(TypeToken.of(String.class))))
            .withOutput(Boolean.class)
            .withAction((criticResult, ctx) -> criticResult.isSuccessful())
            .build();

        strategy.edge(strategy.nodeStart, verification);
        strategy.edge(verification, verificationResult);
        strategy.edge(AIAgentEdge.builder()
            .from(verificationResult)
            .to(strategy.nodeFinish)
            .build());

        AIAgentGraphStrategy<String, Boolean> graphStrategy = strategy.build();
        AIAgent<String, Boolean> positiveAgent = buildVerificationAgent(model, graphStrategy);
        AIAgent<String, Boolean> negativeAgent = buildVerificationAgent(model, graphStrategy);

        Boolean result = positiveAgent.run("Paris is the capital of France.", null);
        Boolean falseResult = negativeAgent.run("The Sun orbits around the Earth.", null);

        assertNotNull(result);
        assertTrue(result);
        assertNotNull(falseResult);
        assertFalse(falseResult);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithFinishToolSubgraph(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        Assumptions.assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model does not support tools");

        FinishFormatterTools finishTools = new FinishFormatterTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(finishTools).build();
        EventRecorder events = new EventRecorder();
        @SuppressWarnings("unchecked")
        Tool<String, String> finishTool = (Tool<String, String>) (Tool<?, ?>) finishTools.getTool("finalizeResult");

        var strategy = AIAgentGraphStrategy.builder("java-finish-tool-subgraph")
            .withInput(String.class)
            .withOutput(String.class);

        var finishSubgraph = AIAgentSubgraph.builder("finish-format-subgraph")
            .withInput(String.class)
            .withFinishTool(finishTool)
            .withTask(input -> "Summarize this in 3 words: " + input)
            .build();

        strategy.edge(strategy.nodeStart, finishSubgraph);
        strategy.edge(finishSubgraph, strategy.nodeFinish);

        AIAgent<String, String> agent = AIAgent.builder()
            .graphStrategy(strategy.build())
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a formatter assistant. Produce a short summary.")
            .toolRegistry(toolRegistry)
            .install(EventHandler.Feature, config -> config.onToolCallStarting(context -> events.toolNames.add(context.getToolName())))
            .build();

        String result = agent.run("Java graph strategy finish tool formatting", null);

        assertNotNull(result);
        assertTrue(result.startsWith("FINAL:"), "Result should be formatted by the finish tool");
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithHistoryCompressionNode(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        EventRecorder events = new EventRecorder();

        var strategy = AIAgentGraphStrategy.builder("java-history-compression")
            .withInput(String.class)
            .withOutput(String.class);

        var firstLlm = AIAgentNode.llmRequest(true, "first-llm");
        var extractFirstResponse = AIAgentNode.builder("extract-first-response")
            .withInput(Message.Response.class)
            .withOutput(String.class)
            .withAction((response, ctx) -> assistantContent(response, "No response"))
            .build();
        var compress = new CompressHistoryNodeBuilder("compress")
            .withInput(String.class)
            .compressionStrategy(HistoryCompressionStrategy.WholeHistory)
            .preserveMemory(true)
            .build();
        var finalLlm = AIAgentNode.llmRequest(true, "final-llm");
        var extractFinalResponse = AIAgentNode.builder("extract-final-response")
            .withInput(Message.Response.class)
            .withOutput(String.class)
            .withAction((response, ctx) -> assistantContent(response, ""))
            .build();

        strategy.edge(strategy.nodeStart, firstLlm);
        strategy.edge(firstLlm, extractFirstResponse);
        strategy.edge(extractFirstResponse, compress);
        strategy.edge(compress, finalLlm);
        strategy.edge(finalLlm, extractFinalResponse);
        strategy.edge(AIAgentEdge.builder()
            .from(extractFinalResponse)
            .to(strategy.nodeFinish)
            .build());

        AIAgent<String, String> agent = AIAgent.builder()
            .graphStrategy(strategy.build())
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a concise assistant. Always preserve the user's main topic.")
            .install(EventHandler.Feature, config -> config.onNodeExecutionStarting(context -> events.nodeNames.add(context.getNode().getName())))
            .build();

        String result = agent.run(
            "First describe Java graph strategy in one sentence, then restate the topic again after compression.",
            null
        );

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(events.nodeNames.contains("compress"));
    }

    @Test
    public void integration_GraphStrategyWithManualCheckpointCreationUsingInMemoryStorage() {
        LLModel model = OpenAIModels.Chat.GPT4o;
        Models.assumeAvailable(model.getProvider());

        InMemoryPersistenceStorageProvider storage = new InMemoryPersistenceStorageProvider();
        AtomicInteger checkpointNodeRuns = new AtomicInteger(0);
        AtomicInteger finalNodeRuns = new AtomicInteger(0);

        AIAgentGraphStrategy<String, String> strategy = buildManualCheckpointGraph(checkpointNodeRuns, finalNodeRuns);

        AIAgent<String, String> agent = buildPersistenceAgent(model, storage, strategy, "java-manual-checkpoint-agent");
        String firstResult = agent.run("first run", agent.getId());

        List<?> checkpoints = runBlocking(continuation -> storage.getCheckpoints(agent.getId(), null, continuation));
        assertFalse(checkpoints.isEmpty());
        assertEquals(1, checkpointNodeRuns.get());
        assertEquals(1, finalNodeRuns.get());
        assertTrue(firstResult.contains("final-node"));

        AIAgent<String, String> restoredAgent = buildPersistenceAgent(model, storage, strategy, agent.getId());
        String secondResult = restoredAgent.run("restored run", agent.getId());

        assertTrue(secondResult.contains("final-node"));
        assertEquals(1, checkpointNodeRuns.get(), "Checkpoint node should not rerun after restore");
        assertEquals(2, finalNodeRuns.get(), "Downstream node should rerun after restore");
    }

    @Test
    public void integration_GraphStrategyWithFilePersistenceStorage() {
        LLModel model = OpenAIModels.Chat.GPT4o;
        Models.assumeAvailable(model.getProvider());

        JVMFilePersistenceStorageProvider storage = new JVMFilePersistenceStorageProvider(tempDir);
        AtomicInteger checkpointNodeRuns = new AtomicInteger(0);
        AtomicInteger finalNodeRuns = new AtomicInteger(0);

        AIAgentGraphStrategy<String, String> strategy = buildManualCheckpointGraph(checkpointNodeRuns, finalNodeRuns);

        AIAgent<String, String> agent = buildPersistenceAgent(model, storage, strategy, "java-file-checkpoint-agent");
        String firstResult = agent.run("first run", agent.getId());

        List<?> checkpoints = runBlocking(continuation -> storage.getCheckpoints(agent.getId(), null, continuation));
        assertFalse(checkpoints.isEmpty());
        assertTrue(firstResult.contains("final-node"));

        AIAgent<String, String> restoredAgent = buildPersistenceAgent(model, storage, strategy, agent.getId());
        String secondResult = restoredAgent.run("restored run", agent.getId());

        Object latestCheckpoint = runBlocking(continuation -> storage.getLatestCheckpoint(agent.getId(), null, continuation));
        assertNotNull(latestCheckpoint);
        assertTrue(secondResult.contains("final-node"));
        assertEquals(1, checkpointNodeRuns.get());
        assertEquals(2, finalNodeRuns.get());
    }

    @Test
    public void integration_GraphStrategyRollbackToLatestCheckpointFromInsideNode() {
        LLModel model = OpenAIModels.Chat.GPT4o;
        Models.assumeAvailable(model.getProvider());

        InMemoryPersistenceStorageProvider storage = new InMemoryPersistenceStorageProvider();
        AtomicInteger checkpointRuns = new AtomicInteger(0);
        AtomicInteger downstreamRuns = new AtomicInteger(0);
        AtomicBoolean rolledBack = new AtomicBoolean(false);
        List<String> executionLog = new CopyOnWriteArrayList<>();

        var graph = AIAgentGraphStrategy.builder("java-rollback-graph")
            .withInput(String.class)
            .withOutput(String.class);

        var checkpointNode = AIAgentNode.builder("checkpoint-node")
            .withInput(String.class)
            .withOutput(String.class)
            .withAction((input, ctx) -> {
                checkpointRuns.incrementAndGet();
                executionLog.add("checkpoint-node");
                createCheckpoint(ctx, "checkpoint-node", input);
                return "checkpoint-node:" + input;
            })
            .build();

        var downstreamNode = AIAgentNode.builder("downstream-node")
            .withInput(String.class)
            .withOutput(String.class)
            .withAction((input, ctx) -> {
                downstreamRuns.incrementAndGet();
                executionLog.add("downstream-node");
                return "downstream-node:" + input;
            })
            .build();

        var rollbackNode = AIAgentNode.builder("rollback-node")
            .withInput(String.class)
            .withOutput(String.class)
            .withAction((input, ctx) -> {
                executionLog.add("rollback-node");
                if (rolledBack.compareAndSet(false, true)) {
                    executionLog.add("rollback-performed");
                    rollbackToLatestCheckpoint(ctx);
                    return "rollback-performed";
                }
                executionLog.add("rollback-skipped");
                return "rollback-skipped";
            })
            .build();

        graph.edge(graph.nodeStart, checkpointNode);
        graph.edge(checkpointNode, downstreamNode);
        graph.edge(downstreamNode, rollbackNode);
        graph.edge(AIAgentEdge.builder()
            .from(rollbackNode)
            .to(graph.nodeFinish)
            .build());

        AIAgentGraphStrategy<String, String> strategy = graph.build();

        AIAgent<String, String> agent = buildPersistenceAgent(model, storage, strategy, "java-rollback-agent");
        String result = agent.run("start rollback test", agent.getId());

        assertNotNull(result);
        assertTrue(result.contains("rollback-skipped"));
        assertEquals(1, checkpointRuns.get(), "Checkpoint node should not rerun after rollback");
        assertEquals(2, downstreamRuns.get(), "Downstream node should rerun after rollback");
        assertEquals(1, executionLog.stream().filter("rollback-performed"::equals).count());
        assertEquals(1, executionLog.stream().filter("checkpoint-node"::equals).count());
        assertEquals(2, executionLog.stream().filter("downstream-node"::equals).count());
    }

    private AIAgent<String, String> buildPersistenceAgent(
        LLModel model,
        PersistenceStorageProvider<?> storage,
        AIAgentGraphStrategy<String, String> strategy,
        String id
    ) {
        return AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .id(id)
            .graphStrategy(strategy)
            .install(Persistence.Feature, config -> {
                config.setStorage(storage);
                config.setEnableAutomaticPersistence(false);
            })
            .build();
    }

    private AIAgent<String, Boolean> buildVerificationAgent(
        LLModel model,
        AIAgentGraphStrategy<String, Boolean> strategy
    ) {
        return AIAgent.builder()
            .graphStrategy(strategy)
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a careful verifier.")
            .build();
    }

    private AIAgentGraphStrategy<String, String> buildManualCheckpointGraph(
        AtomicInteger checkpointNodeRuns,
        AtomicInteger finalNodeRuns
    ) {
        var graph = AIAgentGraphStrategy.builder("java-manual-checkpoint-graph")
            .withInput(String.class)
            .withOutput(String.class);

        var checkpointNode = AIAgentNode.builder("checkpoint-node")
            .withInput(String.class)
            .withOutput(String.class)
            .withAction((input, ctx) -> {
                checkpointNodeRuns.incrementAndGet();
                createCheckpoint(ctx, "checkpoint-node", "checkpoint-node:" + input);
                return "checkpoint-node:" + input;
            })
            .build();

        var finalNode = AIAgentNode.builder("final-node")
            .withInput(String.class)
            .withOutput(String.class)
            .withAction((input, ctx) -> {
                finalNodeRuns.incrementAndGet();
                return "final-node:" + input;
            })
            .build();

        graph.edge(graph.nodeStart, checkpointNode);
        graph.edge(checkpointNode, finalNode);
        graph.edge(AIAgentEdge.builder()
            .from(finalNode)
            .to(graph.nodeFinish)
            .build());

        return graph.build();
    }

    private void createCheckpoint(AIAgentGraphContextBase ctx, String nodePath, String lastOutput) {
        Persistence persistence = PersistenceKt.persistence(ctx);
        runBlockingIfRequired(
            EmptyCoroutineContext.INSTANCE,
            continuation -> persistence.createCheckpointAfterNode(
                ctx,
                nodePath,
                lastOutput,
                TypeToken.of(String.class),
                0L,
                null,
                continuation
            )
        );
    }

    private void rollbackToLatestCheckpoint(AIAgentGraphContextBase ctx) {
        Persistence persistence = PersistenceKt.persistence(ctx);
        runBlockingIfRequired(
            EmptyCoroutineContext.INSTANCE,
            continuation -> persistence.rollbackToLatestCheckpoint(ctx, continuation)
        );
    }

    private static String assistantContent(Message.Response response, String fallback) {
        if (response instanceof Message.Assistant) {
            return (response).getContent();
        }
        return fallback;
    }

    private static boolean containsIgnoreCase(String text, String expected) {
        return text.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private static final class EventRecorder {
        final AtomicInteger strategyStarted = new AtomicInteger();
        final AtomicInteger strategyCompleted = new AtomicInteger();
        final List<String> nodeNames = new CopyOnWriteArrayList<>();
        final List<String> subgraphNames = new CopyOnWriteArrayList<>();
        final List<String> completedSubgraphNames = new CopyOnWriteArrayList<>();
        final List<String> toolNames = new CopyOnWriteArrayList<>();
    }

    public static final class CalculatorTools implements ToolSet {
        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription("Adds two numbers together")
        public int add(@LLMDescription("First number") int a, @LLMDescription("Second number") int b) {
            return a + b;
        }

        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription("Multiplies two numbers")
        public int multiply(@LLMDescription("First number") int a, @LLMDescription("Second number") int b) {
            return a * b;
        }
    }

    public static final class FinishFormatterTools implements ToolSet {
        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription("Formats the final answer into a stable FINAL: prefix")
        public String finalizeResult(@LLMDescription("Raw answer") String raw) {
            return "FINAL:" + raw.trim();
        }
    }
}
