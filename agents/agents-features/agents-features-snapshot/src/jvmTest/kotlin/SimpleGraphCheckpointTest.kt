import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.execution.DEFAULT_AGENT_PATH_SEPARATOR
import ai.koog.agents.core.agent.execution.path
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Tests for the Snapshot feature.
 * These tests verify that the agent can create checkpoints and jump to specific execution points.
 */
class SimpleGraphCheckpointTest {

    /**
     * Test that the agent jumps to a specific execution point when using the checkpoint feature.
     * This test verifies that after setting an execution point, the agent continues execution from that point.
     */
    @Test
    fun `test agent jumps to execution point when using checkpoint`() = runTest {
        // Create a mock executor for testing
        val mockExecutor: PromptExecutor = getMockExecutor {
            // No specific mock responses needed for this test
        }

        // Create a tool registry with the SayToUser tool
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        // Create agent config
        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a test agent.")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        // Create an agent with the teleport strategy
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = createTeleportStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
            }
        }

        // Run the agent
        val result = agent.run("Start the test", null)

        // Verify that the result contains the expected output from the teleported node
        assertEquals(
            "Start the test\n" +
                "Node 1 output\n" +
                "Teleported\n" +
                "Node 1 output\n" +
                "Already teleported, passing by\n" +
                "Node 2 output",
            result
        )
    }

    /**
     * Test that the agent can create and save checkpoints.
     * This test verifies that after creating a checkpoint, it can be retrieved from the provider.
     */
    @Test
    fun `test agent creates and saves checkpoints`() = runTest {
        // Create a snapshot provider to store checkpoints
        val checkpointStorageProvider = InMemoryPersistenceStorageProvider()

        // Create a mock executor for testing
        val mockExecutor: PromptExecutor = getMockExecutor {
            // No specific mock responses needed for this test
        }

        // Create a tool registry with the SayToUser tool
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        // Create agent config
        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a test agent.")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agentId = "test-agent-checkpoint"
        val checkpointNodeId = "test-checkpoint-node"
        val checkpointStrategyName = "test-checkpoint-strategy"

        // Create an agent with the checkpoint strategy
        val agent = AIAgent(
            id = agentId,
            promptExecutor = mockExecutor,
            strategy = createCheckpointStrategy(checkpointStrategyName, checkpointNodeId),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistence) {
                storage = checkpointStorageProvider
            }
        }

        // Run the agent
        agent.run("Start the test", null)

        // Verify that a checkpoint was created and saved
        val checkpoint = checkpointStorageProvider.getCheckpoints(agent.id).firstOrNull()
        assertNotNull(checkpoint, "No checkpoint was created")
        val expectedPath = path(agentId, checkpointStrategyName, checkpointNodeId)
        assertEquals(expectedPath, checkpoint?.nodePath, "Checkpoint has incorrect node ID")
    }

    @Test
    fun test_checkpoint_persists_history() = runTest {
        val checkpointStorageProvider = InMemoryPersistenceStorageProvider()

        val mockExecutor: PromptExecutor = getMockExecutor {
            // No specific mock responses needed for this test
        }

        val input = "You are a test agent."
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system(input)
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agentId = "test-agent-checkpoint"
        val checkpointNodeId = "test-checkpoint-node"
        val checkpointStrategyName = "test-checkpoint-strategy"

        // Create an agent with the checkpoint strategy
        val agent = AIAgent(
            id = agentId,
            promptExecutor = mockExecutor,
            strategy = createCheckpointStrategy(checkpointStrategyName, checkpointNodeId),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistence) {
                storage = checkpointStorageProvider
            }
        }

        // Run the agent
        agent.run("Start the test", null)

        // Verify that a checkpoint was created and saved
        val checkpoint = checkpointStorageProvider.getCheckpoints(agent.id).firstOrNull() ?: error("checkpoint is null")

        val expectedPath = "$agentId${DEFAULT_AGENT_PATH_SEPARATOR}$checkpointStrategyName${DEFAULT_AGENT_PATH_SEPARATOR}$checkpointNodeId"
        assertNotNull(checkpoint, "No checkpoint was created")
        assertEquals(expectedPath, checkpoint.nodePath, "Checkpoint has incorrect node ID")
        assertEquals(3, checkpoint.messageHistory.size)
        assertEquals(input, checkpoint.messageHistory[0].content)
        assertEquals("Node 1 output", checkpoint.messageHistory[1].content)
        assertEquals("Node 2 output", checkpoint.messageHistory[2].content)
    }
}
