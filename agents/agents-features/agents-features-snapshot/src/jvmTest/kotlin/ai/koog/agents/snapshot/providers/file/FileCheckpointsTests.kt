import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.providers.file.JVMFilePersistencyStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for agent with file-based checkpoint provider.
 *
 * These tests verify that an agent can use the file-based checkpoint provider
 * to persist and restore its state across executions.
 */
class FileCheckpointsTests {
    private lateinit var tempDir: Path
    private lateinit var provider: JVMFilePersistencyStorageProvider

    val systemPrompt = "You are a test agent."
    val agentConfig = AIAgentConfig(
        prompt = prompt("test") {
            system(systemPrompt)
        },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 20
    )
    val toolRegistry = ToolRegistry {
        tool(SayToUser)
    }

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("agent-checkpoint-test")
        provider = JVMFilePersistencyStorageProvider(tempDir, "testAgentId")
    }

    @AfterTest
    fun cleanup() {
        // Delete the temp directory and all its contents
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.delete(it) }
    }

    @Test
    fun testAgentExecutionWithRollback() = runTest {
        val agentId = "rollbackAgentId"
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createCheckpointGraphWithRollback("checkpointId"),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = agentId
        ) {
            install(Persistency) {
                storage = provider
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "History: You are a test agent.\n" +
                "Node 1 output\n" +
                "Checkpoint created with ID: checkpointId\n" +
                "Node 2 output\n" +
                "Skipped rollback because it was already performed",
            output
        )

        // Verify that the checkpoint was saved to the file system
        val checkpoints = provider.getCheckpoints()
        assertEquals(1, checkpoints.size, "Should have one checkpoint")
        assertEquals("checkpointId", checkpoints.first().checkpointId)
    }

    @Test
    fun testAgentRestorationNoCheckpoint() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistency) {
                storage = provider
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "History: You are a test agent.\n" +
                "Node 1 output\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun testRestoreFromSingleCheckpoint() = runTest {
        val time = Clock.System.now()
        val agentId = "testAgentId"

        val testCheckpoint = AgentCheckpointData(
            checkpointId = "testCheckpointId",
            createdAt = time,
            nodeId = "Node2",
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time))
            )
        )

        provider.saveCheckpoint(testCheckpoint)

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = agentId
        ) {
            install(Persistency) {
                storage = provider
            }
        }

        val output = agent.run("Start the test")

        assertEquals(
            "History: User message\n" +
                "Assistant message\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun testRestoreFromLatestCheckpoint() = runTest {
        val time = Clock.System.now()
        val agentId = "testAgentId"

        val testCheckpoint = AgentCheckpointData(
            checkpointId = "testCheckpointId",
            createdAt = time,
            nodeId = "Node2",
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time))
            )
        )

        val testCheckpoint2 = AgentCheckpointData(
            checkpointId = "testCheckpointId2",
            createdAt = time - 10.seconds,
            nodeId = "Node1",
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.User("Earlier message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Earlier response", metaInfo = ResponseMetaInfo(time))
            )
        )

        provider.saveCheckpoint(testCheckpoint)
        provider.saveCheckpoint(testCheckpoint2)

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = agentId
        ) {
            install(Persistency) {
                storage = provider
            }
        }

        val output = agent.run("Start the test")

        assertEquals(
            "History: User message\n" +
                "Assistant message\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun testAgentWithContinuousPersistence() = runTest {
        val agentId = "continuousAgentId"

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = agentId
        ) {
            install(Persistency) {
                storage = provider

                enableAutomaticPersistency = true
            }
        }

        agent.run("Start the test")

        // Verify that checkpoints were automatically created
        val checkpoints = provider.getCheckpoints()
        assertTrue(checkpoints.isNotEmpty(), "Should have automatically created checkpoints")
    }
}
