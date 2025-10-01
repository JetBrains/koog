import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Tests for verifying node uniqueness requirements with the AgentCheckpoint feature.
 */
class NodeUniquenessCheckpointTest {

    /**
     * Creates a simple node that appends the output to the input.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.simpleNode(
        name: String? = null,
        output: String,
    ): AIAgentNodeDelegate<String, String> = node(name) {
        llm.writeSession {
            updatePrompt { user { text(output) } }
        }
        return@node it + "\n" + output
    }

    /**
     * Creates a strategy with non-unique node names.
     * This is achieved by creating two nodes with the same name at the same level in the graph.
     */
    private fun createNonUniqueNodesStrategy(): AIAgentGraphStrategy<String, String> = strategy("non-unique-nodes-test") {
        // Create two nodes with the same name
        val node1 by simpleNode(
            "DuplicateNode",
            output = "Node 1 output"
        )

        val node2 by simpleNode(
            "UniqueNode",
            output = "Node 2 output"
        )

        // Create a subgraph with a node that has the same name as node1
        val sg1 by subgraph("subgraph1") {
            // This node has the same name as node1 at the same level in the subgraph
            val sgNode1 by simpleNode(
                "DuplicateNode",
                output = "Subgraph node 1 output"
            )

            val sgNode2 by simpleNode(
                "UniqueSubgraphNode",
                output = "Subgraph node 2 output"
            )

            nodeStart then sgNode1 then sgNode2 then nodeFinish
        }

        edge(nodeStart forwardTo node1)
        edge(node1 forwardTo node2)
        edge(node2 forwardTo sg1)
        edge(sg1 forwardTo nodeFinish)
    }

    /**
     * Test that verifies an error is produced when the AgentCheckpoint feature is present
     * and the graph's nodes are non-unique.
     */
    @Test
    fun `test error when AgentCheckpoint feature is present and nodes are non-unique`() = runTest {
        // Create a mock executor
        val mockExecutor: PromptExecutor = getMockExecutor {}

        // Create a tool registry
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

        // Create an agent with non-unique node names and AgentCheckpoint feature
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = createNonUniqueNodesStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            // Install the AgentCheckpoint feature
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
            }
        }

        // The exception should be thrown when the agent is started, not when the feature is installed
        assertFailsWith<IllegalArgumentException> {
            agent.run("Start the test")
        }
    }

    /**
     * Test that verifies no error occurs when AgentCheckpoint feature is not present
     * and graph's nodes are non-unique.
     */
    @Test
    fun `test no error when AgentCheckpoint feature is not present and nodes are non-unique`() = runTest {
        // Create a mock executor
        val mockExecutor: PromptExecutor = getMockExecutor {}

        // Create a tool registry
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

        // Create an agent with non-unique node names but without AgentCheckpoint feature
        // This should not throw an exception
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = createNonUniqueNodesStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        )

        // Run the agent to verify it works without the AgentCheckpoint feature
        agent.run("Start the test")
    }
}
