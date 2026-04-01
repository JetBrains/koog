import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.agent.execution.path
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.runFromCheckpoint
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class RunFromCheckpointTest {
    private val systemPrompt = "You are a test agent."
    private val agentConfig = AIAgentConfig(
        prompt = prompt("test") {
            system(systemPrompt)
        },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 20
    )
    private val toolRegistry = ToolRegistry {
        tool(SayToUser)
    }
    private val serializer = KotlinxSerializer()

    @Test
    fun testRunFromCheckpointOnAgent() = runTest {
        val sessionId = "test-session"
        val time = Clock.System.now()

        val checkpoint = AgentCheckpointData(
            checkpointId = "checkpoint-1",
            createdAt = time,
            nodePath = path(sessionId, "straight-forward", "Node2"),
            lastOutput = JSONPrimitive("Node 2 output"),
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time))
            ),
            version = 0
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        )

        val output = agent.runFromCheckpoint(
            agentInput = "Start the test",
            checkpoint = checkpoint,
            sessionId = sessionId,
        )

        // Agent restores at Node2 (after it), so only History Node runs.
        // History node collects message history which was restored from the checkpoint.
        assertEquals(
            "History: User message\n" +
                "Assistant message",
            output
        )
    }

    @Test
    fun testRunFromCheckpointOnSession() = runTest {
        val sessionId = "test-session"
        val time = Clock.System.now()

        val checkpoint = AgentCheckpointData(
            checkpointId = "checkpoint-1",
            createdAt = time,
            nodePath = path(sessionId, "straight-forward", "Node1"),
            lastOutput = JSONPrimitive("Node 1 output"),
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time))
            ),
            version = 0
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        )

        val session = agent.createSession(sessionId)
        val output = session.runFromCheckpoint(
            input = "Start the test",
            checkpoint = checkpoint,
        )

        // Agent restores at Node1 (after it), so Node2 and History Node run.
        // History node collects message history: restored checkpoint messages + Node2 appended message.
        assertEquals(
            "History: User message\n" +
                "Assistant message\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun testRunFromCheckpointWithMessageHistoryOnlyStrategy() = runTest {
        val sessionId = "test-session-mh"
        val time = Clock.System.now()

        val checkpoint = AgentCheckpointData(
            checkpointId = "checkpoint-1",
            createdAt = time,
            nodePath = path(sessionId, "straight-forward", "Node1"),
            lastOutput = JSONPrimitive("Node 1 output"),
            messageHistory = listOf(
                Message.User("Restored message", metaInfo = RequestMetaInfo(time)),
            ),
            version = 0
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        )

        val output = agent.runFromCheckpoint(
            agentInput = "Start the test",
            checkpoint = checkpoint,
            rollbackStrategy = RollbackStrategy.MessageHistoryOnly,
            sessionId = sessionId,
        )

        // Restores at Node1 (after it), so Node2 and History Node run.
        // Message history is from the checkpoint + Node2 appended message.
        assertEquals(
            "History: Restored message\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun testRunFromCheckpointWithoutPersistenceFeature() = runTest {
        // This test explicitly verifies that runFromCheckpoint works
        // without the Persistence feature installed on the agent.
        val sessionId = "test-session-np"
        val time = Clock.System.now()

        val checkpoint = AgentCheckpointData(
            checkpointId = "checkpoint-no-persistence",
            createdAt = time,
            nodePath = path(sessionId, "straight-forward", "Node1"),
            lastOutput = JSONPrimitive("Node 1 output"),
            messageHistory = listOf(
                Message.System("You are a test agent.", metaInfo = RequestMetaInfo(time)),
                Message.User("Hello", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Hi there", metaInfo = ResponseMetaInfo(time)),
            ),
            version = 0
        )

        // Agent created without any Persistence feature
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        )

        val output = agent.runFromCheckpoint(
            agentInput = "Ignored input",
            checkpoint = checkpoint,
            sessionId = sessionId,
        )

        assertEquals(
            "History: You are a test agent.\n" +
                "Hello\n" +
                "Hi there\n" +
                "Node 2 output",
            output
        )
    }
}
