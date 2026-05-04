package ai.koog.agents.snapshot.feature;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.agent.context.RollbackStrategy;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.ext.tool.SayToUser;
import ai.koog.agents.testing.tools.MockExecutorBuilder;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.message.ResponseMetaInfo;
import ai.koog.serialization.JSONElementKt;
import ai.koog.serialization.kotlinx.KotlinxSerializer;
import kotlin.time.Clock;
import kotlin.time.Instant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Java API tests for {@link Persistence#runFromCheckpoint}.
 * Mirrors the Kotlin RunFromCheckpointTest to verify Java interop.
 */
public class RunFromCheckpointJavaTest {

    private static final String SYSTEM_PROMPT = "You are a test agent.";

    private static final AIAgentConfig agentConfig = new AIAgentConfig(
        Prompt.builder("test").system(SYSTEM_PROMPT).build(),
        OpenAIModels.Chat.GPT4o,
        20,
        null,
        null
    );

    private static final ToolRegistry toolRegistry = ToolRegistry.builder()
        .tool(SayToUser.INSTANCE)
        .build();

    private static final KotlinxSerializer serializer = new KotlinxSerializer();

    private static String nodePath(String sessionId, String subgraph, String node) {
        return sessionId + "/" + subgraph + "/" + node;
    }

    @SuppressWarnings("unchecked")
    private AIAgent<String, String> createAgent() {
        return (AIAgent<String, String>) AIAgent.builder()
            .promptExecutor(new MockExecutorBuilder(serializer).build())
            .agentConfig(agentConfig)
            .toolRegistry(toolRegistry)
            .graphStrategy(JavaTestHelper.straightForwardGraph())
            .build();
    }

    @Test
    public void testRunFromCheckpointOnAgent() {
        String sessionId = "test-session";
        Instant time = Clock.System.INSTANCE.now();

        AgentCheckpointData checkpoint = new AgentCheckpointData(
            "checkpoint-1",
            time,
            nodePath(sessionId, "straight-forward", "Node2"),
            null,
            JSONElementKt.JSONPrimitive("Node 2 output"),
            List.of(
                new Message.User("User message", new RequestMetaInfo(time, null)),
                new Message.Assistant("Assistant message", new ResponseMetaInfo(time, null, null, null))
            ),
            0L,
            null
        );

        AIAgent<String, String> agent = createAgent();

        String output = Persistence.runFromCheckpoint(
            agent,
            "Start the test",
            checkpoint,
            RollbackStrategy.Default,
            sessionId
        );

        // Agent restores at Node2 (after it), so only History Node runs.
        // History node collects message history which was restored from the checkpoint.
        assertEquals(
            "History: User message\nAssistant message",
            output
        );
    }

    @Test
    public void testRunFromCheckpointWithMessageHistoryOnlyStrategy() {
        String sessionId = "test-session-mh";
        Instant time = Clock.System.INSTANCE.now();

        AgentCheckpointData checkpoint = new AgentCheckpointData(
            "checkpoint-1",
            time,
            nodePath(sessionId, "straight-forward", "Node1"),
            null,
            JSONElementKt.JSONPrimitive("Node 1 output"),
            List.of(
                new Message.User("Restored message", new RequestMetaInfo(time, null))
            ),
            0L,
            null
        );

        AIAgent<String, String> agent = createAgent();

        String output = Persistence.runFromCheckpoint(
            agent,
            "Start the test",
            checkpoint,
            RollbackStrategy.MessageHistoryOnly,
            sessionId
        );

        // Restores at Node1 (after it), so Node2 and History Node run.
        assertEquals(
            "History: Restored message\nNode 2 output",
            output
        );
    }

    @Test
    public void testRunFromCheckpointWithoutPersistenceFeature() {
        String sessionId = "test-session-np";
        Instant time = Clock.System.INSTANCE.now();

        AgentCheckpointData checkpoint = new AgentCheckpointData(
            "checkpoint-no-persistence",
            time,
            nodePath(sessionId, "straight-forward", "Node1"),
            null,
            JSONElementKt.JSONPrimitive("Node 1 output"),
            List.of(
                new Message.System("You are a test agent.", new RequestMetaInfo(time, null)),
                new Message.User("Hello", new RequestMetaInfo(time, null)),
                new Message.Assistant("Hi there", new ResponseMetaInfo(time, null, null, null))
            ),
            0L,
            null
        );

        AIAgent<String, String> agent = createAgent();

        String output = Persistence.runFromCheckpoint(
            agent,
            "Ignored input",
            checkpoint,
            RollbackStrategy.Default,
            sessionId
        );

        assertEquals(
            "History: You are a test agent.\nHello\nHi there\nNode 2 output",
            output
        );
    }

    @Test
    public void testRunFromCheckpointUsesJvmOverloadDefaults() {
        String sessionId = "test-session-defaults";
        Instant time = Clock.System.INSTANCE.now();

        AgentCheckpointData checkpoint = new AgentCheckpointData(
            "checkpoint-defaults",
            time,
            nodePath(sessionId, "straight-forward", "Node1"),
            null,
            JSONElementKt.JSONPrimitive("Node 1 output"),
            List.of(
                new Message.User("User message", new RequestMetaInfo(time, null)),
                new Message.Assistant("Assistant message", new ResponseMetaInfo(time, null, null, null))
            ),
            0L,
            null
        );

        AIAgent<String, String> agent = createAgent();

        String output = Persistence.runFromCheckpoint(
            agent,
            "Start the test",
            checkpoint
        );

        assertEquals(
            "History: User message\nAssistant message\nNode 2 output",
            output
        );
    }
}
