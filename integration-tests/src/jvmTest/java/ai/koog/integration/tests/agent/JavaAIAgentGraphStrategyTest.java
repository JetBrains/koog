package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase;
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
import ai.koog.prompt.llm.LLMCapability;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import ai.koog.serialization.TypeToken;
import kotlin.coroutines.Continuation;
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

import static org.junit.jupiter.api.Assertions.*;

public class JavaAIAgentGraphStrategyTest extends KoogJavaTestBase {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithTypedNodeAndLlmNode(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .<String, String>graphStrategy("java-typed-node-graph", b -> {
                var graph = b.withInput(String.class).withOutput(String.class);

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

                graph.edge(graph.nodeStart, preprocess);
                graph.edge(preprocess, llm);
                graph.edge(llm, extractContent);
                graph.edge(extractContent, graph.nodeFinish);

                return graph.build();
            })
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

        AIAgent<String, String> agent = AIAgent.builder()
            .<String, String>graphStrategy("java-subgraph-limited-tools", b -> {
                var graph = b.withInput(String.class).withOutput(String.class);

                var calcSubgraph = AIAgentSubgraph.builder("calc-subgraph")
                    .limitedTools(List.of(calculatorTools.getTool("multiply")))
                    .withInput(String.class)
                    .withOutput(String.class)
                    .withTask(input -> "Use the multiply tool to calculate 7 * 8. Return only the numeric answer.")
                    .build();

                graph.edge(graph.nodeStart, calcSubgraph);
                graph.edge(calcSubgraph, graph.nodeFinish);

                return graph.build();
            })
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

        AIAgent<String, String> agent = AIAgent.builder()
            .<String, String>graphStrategy("java-graph-events", b -> {
                var graph = b.withInput(String.class).withOutput(String.class);

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

                graph.edge(graph.nodeStart, prepare);
                graph.edge(prepare, calcSubgraph);
                graph.edge(calcSubgraph, graph.nodeFinish);

                return graph.build();
            })
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
        assertTrue(events.nodeNames.size() > 0);
        assertTrue(events.subgraphNames.contains("tool-subgraph"));
        assertTrue(events.completedSubgraphNames.contains("tool-subgraph"));
        assertTrue(events.nodeNames.contains("prepare"));
        assertTrue(events.toolNames.contains("multiply"));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithVerificationPath(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, Boolean> agent = AIAgent.builder()
            .<String, Boolean>graphStrategy("java-graph-verification", b -> {
                var graph = b.withInput(String.class).withOutput(Boolean.class);

                var verification = AIAgentSubgraph.builder("verification-subgraph")
                    .withInput(String.class)
                    .withVerification(input -> "Answer true only if the statement is factually correct: " + input)
                    .build();
                var verificationResult = AIAgentNode.builder("verification-result")
                    .withInput(ai.koog.agents.ext.agent.CriticResult.class)
                    .withOutput(Boolean.class)
                    .withAction((criticResult, ctx) -> ((ai.koog.agents.ext.agent.CriticResult<?>) criticResult).isSuccessful())
                    .build();

                graph.edge(graph.nodeStart, verification);
                graph.edge(verification, verificationResult);
                graph.edge(verificationResult, graph.nodeFinish);

                return graph.build();
            })
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a careful verifier.")
            .build();

        Boolean result = agent.run("Paris is the capital of France.", null);

        assertNotNull(result);
        assertTrue(result);
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

        AIAgent<String, String> agent = AIAgent.builder()
            .<String, String>graphStrategy("java-finish-tool-subgraph", b -> {
                var graph = b.withInput(String.class).withOutput(String.class);

                var finishSubgraph = AIAgentSubgraph.builder("finish-format-subgraph")
                    .withInput(String.class)
                    .withFinishTool(finishTool)
                    .withTask(input -> "Summarize this in 3 words: " + input)
                    .build();

                graph.edge(graph.nodeStart, finishSubgraph);
                graph.edge(finishSubgraph, graph.nodeFinish);

                return graph.build();
            })
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a formatter assistant. Produce a short summary.")
            .toolRegistry(toolRegistry)
            .install(EventHandler.Feature, config -> config.onToolCallStarting(context -> events.toolNames.add(context.getToolName())))
            .build();

        String result = agent.run("Java graph strategy finish tool formatting", null);

        assertNotNull(result);
        assertTrue(events.toolNames.contains(finishTools.getName()));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithHistoryCompressionNode(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        EventRecorder events = new EventRecorder();

        AIAgent<String, String> agent = AIAgent.builder()
            .<String, String>graphStrategy("java-history-compression", b -> {
                var graph = b.withInput(String.class).withOutput(String.class);

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

                graph.edge(graph.nodeStart, firstLlm);
                graph.edge(firstLlm, extractFirstResponse);
                graph.edge(extractFirstResponse, compress);
                graph.edge(compress, finalLlm);
                graph.edge(finalLlm, extractFinalResponse);
                graph.edge(extractFinalResponse, graph.nodeFinish);

                return graph.build();
            })
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

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithManualCheckpointCreationUsingInMemoryStorage(LLModel model) {
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

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithFilePersistenceStorage(LLModel model) {
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

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyRollbackToLatestCheckpointFromInsideNode(LLModel model) {
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
        graph.edge(rollbackNode, graph.nodeFinish);

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
        graph.edge(finalNode, graph.nodeFinish);

        return graph.build();
    }

    private void createCheckpoint(AIAgentGraphContextBase ctx, String nodePath, String lastOutput) {
        Persistence persistence = PersistenceKt.persistence(ctx);
        runBlocking(new SuspendFunction<Object>() {
            @Override
            public Object invoke(Continuation<? super Object> continuation) {
                return persistence.createCheckpointAfterNode(
                    ctx,
                    nodePath,
                    lastOutput,
                    TypeToken.of(String.class),
                    0L,
                    null,
                    continuation
                );
            }
        });
    }

    private void rollbackToLatestCheckpoint(AIAgentGraphContextBase ctx) {
        Persistence persistence = PersistenceKt.persistence(ctx);
        runBlocking(new SuspendFunction<Object>() {
            @Override
            public Object invoke(Continuation<? super Object> continuation) {
                return persistence.rollbackToLatestCheckpoint(ctx, continuation);
            }
        });
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

    public static final class FinishFormatterTools implements ToolSet {
        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription(description = "Formats the final answer into a stable FINAL: prefix")
        public String finalizeResult(@LLMDescription(description = "Raw answer") String raw) {
            return "FINAL:" + raw.trim();
        }
    }
}
