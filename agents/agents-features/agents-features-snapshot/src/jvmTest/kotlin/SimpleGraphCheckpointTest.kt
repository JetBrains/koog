import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegateBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.AgentCheckpoint
import ai.koog.agents.snapshot.feature.withCheckpoints
import ai.koog.agents.snapshot.providers.InMemoryAgentCheckpointStorageProvider
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
        // Create a snapshot provider to store checkpoints
        val snapshotProvider = InMemoryAgentCheckpointStorageProvider()

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
            install(AgentCheckpoint) {
                snapshotProvider(snapshotProvider)
            }
        }

        // Run the agent
        val result = agent.runAndGetResult("Start the test")

        // Verify that the result contains the expected output from the teleported node
        assertEquals(
                "Start the test\n" +
                "Node 1 output\n" +
                "Teleported\n" +
                "Node 1 output\n" +
                "Already teleported, passing by\n" +
                "Node 2 output", result)
    }

    /**
     * Test that the agent can create and save checkpoints.
     * This test verifies that after creating a checkpoint, it can be retrieved from the provider.
     */
    @Test
    fun `test agent creates and saves checkpoints`() = runTest {
        // Create a snapshot provider to store checkpoints
        val checkpointStorageProvider = InMemoryAgentCheckpointStorageProvider()

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

        // Create an agent with the checkpoint strategy
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = createCheckpointStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(AgentCheckpoint) {
                snapshotProvider(checkpointStorageProvider)
            }
        }

        // Run the agent
        agent.run("Start the test")

        // Verify that a checkpoint was created and saved
        val checkpoint = checkpointStorageProvider.getCheckpoint("snapshot-id")
        assertNotNull(checkpoint, "No checkpoint was created")
        assertEquals("checkpointNode", checkpoint?.nodeId, "Checkpoint has incorrect node ID")
    }

    /**
     * Creates a strategy with a teleport node that jumps to a specific execution point.
     */
    private fun createTeleportStrategy() = strategy("teleport-test") {
        val node1 by simpleNode(
            "Node1",
            output = "Node 1 output"
        )

        val node2 by simpleNode(
            "Node2",
            output = "Node 2 output"
        )
        val teleportNode by teleportNode()

        edge(nodeStart forwardTo node1)
        edge(node1 forwardTo teleportNode)
        edge(teleportNode forwardTo node2)
        edge(node2 forwardTo nodeFinish)
    }

    /**
     * Creates a strategy with a checkpoint node that creates and saves a checkpoint.
     */
    private fun createCheckpointStrategy() = strategy("checkpoint-test") {
        val node1 by simpleNode(
            "Node1",
            output = "Node 1 output"
        )

        val checkpointNode by nodeCreateCheckpoint(
            "checkpointNode"
        )

        val node2 by simpleNode(
            "Node2",
            output = "Node 2 output"
        )

        edge(nodeStart forwardTo node1)
        edge(node1 forwardTo checkpointNode)
        edge(checkpointNode forwardTo node2)
        edge(node2 forwardTo nodeFinish)
    }

    /**
     * Creates a simple node that appends the output to the input.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.simpleNode(
        name: String? = null,
        output: String,
    ): AIAgentNodeDelegateBase<String, String> = node(name) {
        return@node it + "\n" + output
    }

    // Flag to track whether teleportation has occurred
    private var hasTeleported = false

    /**
     * Creates a teleport node that jumps to a specific execution point.
     * Only teleports once to avoid infinite loops.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.teleportNode(
        name: String? = null,
    ): AIAgentNodeDelegateBase<String, String> = node(name) {
        val ctx = this
        if (!hasTeleported) {
            hasTeleported = true
            withCheckpoints {
                val history = llm.readSession { this.prompt.messages }
                setExecutionPoint(ctx, "Node1", history, "$it\nTeleported")
                return@withCheckpoints "Teleported"
            }
        } else {
            // If we've already teleported, just return the input
            return@node "$it\nAlready teleported, passing by"
        }
    }

    /**
     * Creates a checkpoint node that creates and saves a checkpoint.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeCreateCheckpoint(
        name: String? = null,
    ): AIAgentNodeDelegateBase<String, String> = node(name) {
        val ctx = this
        val input = it
        withCheckpoints {
            val checkpoint = createCheckpoint("snapshot-id", ctx, currentNodeId ?: error("currentNodeId not set"), input)
            saveCheckpoint(checkpoint.checkpointId, checkpoint)

            val snapshot = it + "Snapshot created"
            return@withCheckpoints snapshot
        }
    }
}
