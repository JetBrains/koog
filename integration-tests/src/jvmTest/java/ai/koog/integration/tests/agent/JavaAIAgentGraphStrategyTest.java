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
import ai.koog.agents.snapshot.feature.AgentCheckpointData;
import ai.koog.agents.snapshot.feature.Persistence;
import ai.koog.agents.snapshot.feature.PersistenceKt;
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider;
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider;
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.Models;
import ai.koog.integration.tests.utils.annotations.Retry;
import ai.koog.agents.ext.agent.CriticResult;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.llm.LLMCapability;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import ai.koog.serialization.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.koog.agents.core.utils.CoroutineUtilsKt.runBlockingIfRequired;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class JavaAIAgentGraphStrategyTest extends KoogJavaTestBase {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithTypedNodeAndLlmNode(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        EventRecorder events = new EventRecorder();

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
            .install(EventHandler.Feature, config -> {
                config.onNodeExecutionStarting(context -> events.recordNodeStart(context.getNode().getName()));
                config.onNodeExecutionCompleted(context -> events.recordNodeCompleted(context.getNode().getName()));
            })
            .build();

        String result = agent.run("Java graph API", null);

        assertAll(
            () -> assertNotNull(result, "Graph result should not be null"),
            () -> assertFalse(result.isBlank(), "Graph result should not be blank"),
            () -> assertTrue(containsIgnoreCase(result, "hello"), "Graph result should contain the expected hello reply, but was: " + result),
            () -> assertEquals(
                List.of("preprocess", "llm", "extract-content"),
                withoutGraphBoundaryNodes(events.nodeNames),
                "Expected node execution order for typed-node graph"
            ),
            () -> assertEquals(
                List.of("preprocess", "llm", "extract-content"),
                withoutGraphBoundaryNodes(events.completedNodeNames),
                "Expected node completion order for typed-node graph"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyWithTaskSubgraphAndLimitedTools(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        Assumptions.assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model does not support tools");
        assumeNoGoogleToolCalling(model);

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
            .withTask(input -> "You MUST call the multiply tool exactly once to calculate 7 * 8. Return only 56.")
            .build();

        strategy.edge(strategy.nodeStart, calcSubgraph);
        strategy.edge(calcSubgraph, strategy.nodeFinish);

        AIAgent<String, String> agent = AIAgent.builder()
            .graphStrategy(strategy.build())
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator assistant. Use only the provided calculator tools when calculation is required.")
            .toolRegistry(toolRegistry)
            .maxIterations(100)
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(context -> events.toolNames.add(context.getToolName()));
                config.onSubgraphExecutionStarting(context -> events.subgraphNames.add(context.getSubgraph().getName()));
            })
            .build();

        String result = agent.run("Calculate 7 times 8", null);

        long multiplyEventCount = events.toolNames.stream().filter("multiply"::equals).count();

        assertAll(
            () -> assertNotNull(result, "Subgraph result should not be null"),
            () -> assertFalse(result.isBlank(), "Subgraph result should not be blank"),
            () -> assertTrue(result.contains("56"), "Result should contain the multiplication result, but was: " + result),
            () -> assertTrue(events.toolNames.contains("multiply"), "Expected multiply tool event to be emitted"),
            () -> assertFalse(events.toolNames.contains("add"), "Limited tool subgraph should not expose the add tool"),
            () -> assertEquals(1L, multiplyEventCount, "Multiply tool should be requested exactly once"),
            () -> assertEquals(1, calculatorTools.multiplyCalls.get(), "Multiply tool should be invoked exactly once"),
            () -> assertEquals(0, calculatorTools.addCalls.get(), "Add tool should never be invoked"),
            () -> assertTrue(events.subgraphNames.contains("calc-subgraph"), "Expected calc-subgraph start event to be emitted")
        );
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_GraphStrategyShouldEmitStrategyNodeSubgraphAndToolEvents(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        Assumptions.assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model does not support tools");
        assumeNoGoogleToolCalling(model);

        CalculatorTools calculatorTools = new CalculatorTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(calculatorTools).build();
        EventRecorder events = new EventRecorder();

        var strategy = AIAgentGraphStrategy.builder("java-graph-events")
            .withInput(String.class)
            .withOutput(String.class);

        var prepare = AIAgentNode.builder("prepare")
            .withInput(String.class)
            .withOutput(String.class)
            .withAction((input, ctx) -> "You MUST call the multiply tool exactly once to calculate 6 * 9. Return only 54.")
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
            .maxIterations(100)
            .install(EventHandler.Feature, config -> {
                config.onStrategyStarting(context -> events.recordStrategyStarted());
                config.onStrategyCompleted(context -> events.recordStrategyCompleted());
                config.onNodeExecutionStarting(context -> events.recordNodeStart(context.getNode().getName()));
                config.onNodeExecutionCompleted(context -> events.recordNodeCompleted(context.getNode().getName()));
                config.onSubgraphExecutionStarting(context -> events.recordSubgraphStart(context.getSubgraph().getName()));
                config.onSubgraphExecutionCompleted(context -> events.recordSubgraphCompleted(context.getSubgraph().getName()));
                config.onToolCallStarting(context -> events.recordToolCall(context.getToolName()));
            })
            .build();

        String result = agent.run("event run", null);

        assertAll(
            () -> assertNotNull(result, "Event graph result should not be null"),
            () -> assertEquals(1, events.strategyStarted.get(), "Strategy should start exactly once"),
            () -> assertEquals(1, events.strategyCompleted.get(), "Strategy should complete exactly once"),
            () -> assertFalse(events.nodeNames.isEmpty(), "At least one node should have executed"),
            () -> assertTrue(result.contains("54"), "Result should contain the multiplication result, but was: " + result),
            () -> assertTrue(events.subgraphNames.contains("tool-subgraph"), "Expected tool-subgraph start event"),
            () -> assertTrue(events.completedSubgraphNames.contains("tool-subgraph"), "Expected tool-subgraph completion event"),
            () -> assertTrue(events.nodeNames.contains("prepare"), "Expected prepare node start event"),
            () -> assertTrue(events.completedNodeNames.contains("prepare"), "Expected prepare node completion event"),
            () -> assertTrue(events.toolNames.contains("multiply"), "Expected multiply tool event"),
            () -> assertBefore(events.eventLog, "strategy-started", "node-start:prepare", "Strategy should start before prepare node"),
            () -> assertBefore(events.eventLog, "node-start:prepare", "subgraph-start:tool-subgraph", "Prepare node should start before subgraph"),
            () -> assertBefore(events.eventLog, "subgraph-start:tool-subgraph", "tool:multiply", "Subgraph should start before the multiply tool is called"),
            () -> assertBefore(events.eventLog, "tool:multiply", "subgraph-completed:tool-subgraph", "Tool call should happen before subgraph completion"),
            () -> assertBefore(events.eventLog, "subgraph-completed:tool-subgraph", "strategy-completed", "Strategy should complete after the subgraph completes")
        );
    }

    private static void assumeNoGoogleToolCalling(LLModel model) {
        assumeTrue(
            !"google".equalsIgnoreCase(model.getProvider().getId()),
            "Skipping Google models until thought_signature support is fixed (KG-722/KG-596)"
        );
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Retry
    public void integration_GraphStrategyWithVerificationPath(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeNoGoogleToolCalling(model);
        EventRecorder positiveEvents = new EventRecorder();
        EventRecorder negativeEvents = new EventRecorder();

        var strategy = AIAgentGraphStrategy.builder("java-graph-verification")
            .withInput(String.class)
            .withOutput(Boolean.class);

        var verification = AIAgentSubgraph.builder("verification-subgraph")
            .withInput(String.class)
            .withVerification(input -> "Return true only if the following arithmetic statement is mathematically correct, otherwise return false: " + input)
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
        AIAgent<String, Boolean> positiveAgent = buildVerificationAgent(model, graphStrategy, positiveEvents);
        AIAgent<String, Boolean> negativeAgent = buildVerificationAgent(model, graphStrategy, negativeEvents);

        Boolean result = positiveAgent.run("Paris is the capital of France.", null);
        Boolean falseResult = negativeAgent.run("The Sun orbits around the Earth.", null);

        assertAll(
            () -> assertNotNull(result, "Verification result for the true statement should not be null"),
            () -> assertNotNull(falseResult, "Verification result for the false statement should not be null"),
            () -> assertTrue(positiveEvents.nodeNames.contains("verification-result"), "Expected verification-result node to execute in the positive run"),
            () -> assertTrue(negativeEvents.nodeNames.contains("verification-result"), "Expected verification-result node to execute in the negative run"),
            () -> assertTrue(positiveEvents.subgraphNames.contains("verification-subgraph"), "Expected verification subgraph start event in the positive run"),
            () -> assertTrue(negativeEvents.subgraphNames.contains("verification-subgraph"), "Expected verification subgraph start event in the negative run")
        );
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

        assertAll(
            () -> assertNotNull(result, "Finish-tool graph result should not be null"),
            () -> assertTrue(result.startsWith("FINAL:"), "Result should be formatted by the finish tool, but was: " + result),
            () -> assertTrue(result.length() > "FINAL:".length(), "Finish tool result should contain content after the FINAL: prefix"),
            () -> assertEquals(1, finishTools.finalizeCalls.get(), "Finish tool should be invoked exactly once")
        );
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
            .install(EventHandler.Feature, config -> config.onNodeExecutionStarting(context -> events.recordNodeStart(context.getNode().getName())))
            .build();

        String result = agent.run(
            "First describe Java graph strategy in one sentence, then restate the topic again after compression.",
            null
        );

        assertAll(
            () -> assertNotNull(result, "History-compression graph result should not be null"),
            () -> assertFalse(result.isBlank(), "History-compression graph result should not be blank"),
            () -> assertEquals(
                List.of("first-llm", "extract-first-response", "compress", "final-llm", "extract-final-response"),
                withoutGraphBoundaryNodes(events.nodeNames),
                "Expected history-compression flow to execute all nodes in order"
            ),
            () -> assertBefore(events.nodeNames, "first-llm", "compress", "Compression should happen after the first LLM node"),
            () -> assertBefore(events.nodeNames, "compress", "final-llm", "Compression should happen before the final LLM node")
        );
    }

    @Test
    @Retry
    @Timeout(30)
    public void integration_GraphStrategyWithManualCheckpointCreationUsingInMemoryStorage() {
        LLModel model = OpenAIModels.Chat.GPT4o;
        Models.assumeAvailable(model.getProvider());

        InMemoryPersistenceStorageProvider storage = new InMemoryPersistenceStorageProvider();
        AtomicInteger checkpointNodeRuns = new AtomicInteger(0);
        AtomicInteger finalNodeRuns = new AtomicInteger(0);

        AIAgentGraphStrategy<String, String> strategy = buildManualCheckpointGraph(checkpointNodeRuns, finalNodeRuns);

        AIAgent<String, String> agent = buildPersistenceAgent(model, storage, strategy, "java-manual-checkpoint-agent");
        String firstResult = agent.run("first run", agent.getId());

        List<AgentCheckpointData> checkpoints = getCheckpoints(storage, agent.getId());
        int checkpointNodeRunsAfterFirstRun = checkpointNodeRuns.get();
        int finalNodeRunsAfterFirstRun = finalNodeRuns.get();

        AIAgent<String, String> restoredAgent = buildPersistenceAgent(model, storage, strategy, agent.getId());
        String secondResult = restoredAgent.run("restored run", agent.getId());

        assertAll(
            () -> assertEquals(1, checkpoints.size(), "Expected exactly one checkpoint to be created"),
            () -> assertTrue(checkpoints.get(0).getNodePath().endsWith("/checkpoint-node"), "Checkpoint should be stored for checkpoint-node, but was: " + checkpoints.get(0).getNodePath()),
            () -> assertEquals(1, checkpointNodeRunsAfterFirstRun, "Checkpoint node should run exactly once on the initial execution"),
            () -> assertEquals(1, finalNodeRunsAfterFirstRun, "Final node should run exactly once on the initial execution"),
            () -> assertEquals("final-node:checkpoint-node:first run", firstResult, "Initial run should produce the expected final-node output"),
            () -> assertEquals("final-node:checkpoint-node:first run", secondResult, "Restored run should resume from checkpointed output, not from the new input"),
            () -> assertEquals(1, checkpointNodeRuns.get(), "Checkpoint node should not rerun after restore"),
            () -> assertEquals(2, finalNodeRuns.get(), "Downstream node should rerun after restore")
        );
    }

    @Test
    @Retry
    @Timeout(30)
    public void integration_GraphStrategyWithFilePersistenceStorage() {
        LLModel model = OpenAIModels.Chat.GPT4o;
        Models.assumeAvailable(model.getProvider());

        JVMFilePersistenceStorageProvider storage = new JVMFilePersistenceStorageProvider(tempDir);
        AtomicInteger checkpointNodeRuns = new AtomicInteger(0);
        AtomicInteger finalNodeRuns = new AtomicInteger(0);

        AIAgentGraphStrategy<String, String> strategy = buildManualCheckpointGraph(checkpointNodeRuns, finalNodeRuns);

        AIAgent<String, String> agent = buildPersistenceAgent(model, storage, strategy, "java-file-checkpoint-agent");
        String firstResult = agent.run("first run", agent.getId());

        List<AgentCheckpointData> checkpoints = getCheckpoints(storage, agent.getId());
        int checkpointNodeRunsAfterFirstRun = checkpointNodeRuns.get();
        int finalNodeRunsAfterFirstRun = finalNodeRuns.get();

        AIAgent<String, String> restoredAgent = buildPersistenceAgent(model, storage, strategy, agent.getId());
        String secondResult = restoredAgent.run("restored run", agent.getId());

        AgentCheckpointData latestCheckpoint = getLatestCheckpoint(storage, agent.getId());

        assertAll(
            () -> assertEquals(1, checkpoints.size(), "Expected exactly one file checkpoint to be created"),
            () -> assertTrue(checkpoints.get(0).getNodePath().endsWith("/checkpoint-node"), "Checkpoint should be stored for checkpoint-node, but was: " + checkpoints.get(0).getNodePath()),
            () -> assertEquals(1, checkpointNodeRunsAfterFirstRun, "Checkpoint node should run exactly once on the initial execution"),
            () -> assertEquals(1, finalNodeRunsAfterFirstRun, "Final node should run exactly once on the initial execution"),
            () -> assertEquals("final-node:checkpoint-node:first run", firstResult, "Initial run should produce the expected final-node output"),
            () -> assertNotNull(latestCheckpoint, "Latest checkpoint should be available after file-based persistence"),
            () -> assertTrue(latestCheckpoint.getNodePath().endsWith("/checkpoint-node"), "Latest file checkpoint should point to checkpoint-node, but was: " + latestCheckpoint.getNodePath()),
            () -> assertTrue(hasAnyFiles(tempDir), "File persistence should materialize checkpoint files in the temp directory"),
            () -> assertEquals("final-node:checkpoint-node:first run", secondResult, "Restored run should reuse the stored checkpoint output"),
            () -> assertEquals(1, checkpointNodeRuns.get(), "Checkpoint node should not rerun after file restore"),
            () -> assertEquals(2, finalNodeRuns.get(), "Downstream node should rerun after file restore")
        );
    }

    @Test
    @Retry
    @Timeout(30)
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

        List<AgentCheckpointData> checkpoints = getCheckpoints(storage, agent.getId());

        assertAll(
            () -> assertNotNull(result, "Rollback graph result should not be null"),
            () -> assertTrue(result.contains("rollback-skipped"), "Rollback graph should finish on the post-rollback path, but was: " + result),
            () -> assertEquals(1, checkpointRuns.get(), "Checkpoint node should not rerun after rollback"),
            () -> assertEquals(2, downstreamRuns.get(), "Downstream node should rerun after rollback"),
            () -> assertEquals(1, executionLog.stream().filter("rollback-performed"::equals).count(), "Rollback should be performed exactly once"),
            () -> assertEquals(2, executionLog.stream().filter("rollback-node"::equals).count(), "Rollback node should run before and after rollback"),
            () -> assertEquals(1, executionLog.stream().filter("checkpoint-node"::equals).count(), "Checkpoint node should run only once"),
            () -> assertEquals(2, executionLog.stream().filter("downstream-node"::equals).count(), "Downstream node should run twice"),
            () -> assertEquals(
                List.of(
                    "checkpoint-node",
                    "downstream-node",
                    "rollback-node",
                    "rollback-performed",
                    "downstream-node",
                    "rollback-node",
                    "rollback-skipped"
                ),
                new ArrayList<>(executionLog),
                "Execution order should show rollback to the checkpoint and replay from the downstream node"
            ),
            () -> assertEquals(1, checkpoints.size(), "Rollback should not delete the existing checkpoint"),
            () -> assertTrue(checkpoints.get(0).getNodePath().endsWith("/checkpoint-node"), "Rollback should preserve the checkpoint at checkpoint-node, but was: " + checkpoints.get(0).getNodePath())
        );
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
        AIAgentGraphStrategy<String, Boolean> strategy,
        EventRecorder events
    ) {
        return AIAgent.builder()
            .graphStrategy(strategy)
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a careful verifier.")
            .install(EventHandler.Feature, config -> {
                config.onNodeExecutionStarting(context -> events.recordNodeStart(context.getNode().getName()));
                config.onSubgraphExecutionStarting(context -> events.recordSubgraphStart(context.getSubgraph().getName()));
            })
            .build();
    }

    private List<AgentCheckpointData> getCheckpoints(PersistenceStorageProvider<?> storage, String agentId) {
        return runBlocking(continuation -> storage.getCheckpoints(agentId, null, continuation));
    }

    private AgentCheckpointData getLatestCheckpoint(PersistenceStorageProvider<?> storage, String agentId) {
        return runBlocking(continuation -> storage.getLatestCheckpoint(agentId, null, continuation));
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
            continuation -> persistence.rollbackToLatestCheckpoint(ctx, continuation)
        );
    }

    private static boolean hasAnyFiles(Path directory) {
        try (var files = Files.list(directory)) {
            return files.findAny().isPresent();
        } catch (Exception e) {
            fail("Failed to inspect persistence directory " + directory + ": " + e.getMessage(), e);
            return false;
        }
    }

    private static void assertBefore(List<String> events, String first, String second, String message) {
        int firstIndex = events.indexOf(first);
        int secondIndex = events.indexOf(second);

        assertTrue(firstIndex >= 0, message + " Missing event: " + first + ". Actual events: " + events);
        assertTrue(secondIndex >= 0, message + " Missing event: " + second + ". Actual events: " + events);
        assertTrue(firstIndex < secondIndex, message + ". Actual events: " + events);
    }

    private static List<String> withoutGraphBoundaryNodes(List<String> nodeNames) {
        return nodeNames.stream()
            .filter(nodeName -> !"__start__".equals(nodeName) && !"__finish__".equals(nodeName))
            .toList();
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
        final List<String> completedNodeNames = new CopyOnWriteArrayList<>();
        final List<String> subgraphNames = new CopyOnWriteArrayList<>();
        final List<String> completedSubgraphNames = new CopyOnWriteArrayList<>();
        final List<String> toolNames = new CopyOnWriteArrayList<>();

        final List<String> eventLog = new CopyOnWriteArrayList<>();

        void recordStrategyStarted() {
            strategyStarted.incrementAndGet();
            eventLog.add("strategy-started");
        }

        void recordStrategyCompleted() {
            strategyCompleted.incrementAndGet();
            eventLog.add("strategy-completed");
        }

        void recordNodeStart(String nodeName) {
            nodeNames.add(nodeName);
            eventLog.add("node-start:" + nodeName);
        }

        void recordNodeCompleted(String nodeName) {
            completedNodeNames.add(nodeName);
            eventLog.add("node-completed:" + nodeName);
        }

        void recordSubgraphStart(String subgraphName) {
            subgraphNames.add(subgraphName);
            eventLog.add("subgraph-start:" + subgraphName);
        }

        void recordSubgraphCompleted(String subgraphName) {
            completedSubgraphNames.add(subgraphName);
            eventLog.add("subgraph-completed:" + subgraphName);
        }

        void recordToolCall(String toolName) {
            toolNames.add(toolName);
            eventLog.add("tool:" + toolName);
        }
    }

    public static final class CalculatorTools implements ToolSet {
        final AtomicInteger addCalls = new AtomicInteger();
        final AtomicInteger multiplyCalls = new AtomicInteger();

        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription("Adds two numbers together")
        public int add(@LLMDescription("First number") int a, @LLMDescription("Second number") int b) {
            addCalls.incrementAndGet();
            return a + b;
        }

        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription("Multiplies two numbers")
        public int multiply(@LLMDescription("First number") int a, @LLMDescription("Second number") int b) {
            multiplyCalls.incrementAndGet();
            return a * b;
        }
    }

    public static final class FinishFormatterTools implements ToolSet {
        final AtomicInteger finalizeCalls = new AtomicInteger();

        @ai.koog.agents.core.tools.annotations.Tool
        @LLMDescription("Formats the final answer into a stable FINAL: prefix")
        public String finalizeResult(@LLMDescription("Raw answer") String raw) {
            finalizeCalls.incrementAndGet();
            return "FINAL:" + raw.trim();
        }
    }
}
