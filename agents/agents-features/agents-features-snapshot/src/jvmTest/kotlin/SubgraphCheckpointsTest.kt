import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for checkpoint functionality in subgraphs.
 */
class SubgraphCheckpointsTest {
    val systemPrompt = "You are a test agent."
    val agentConfig = AIAgentConfig(
        prompt = prompt("test") {
            system(systemPrompt)
        },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 30
    )
    val toolRegistry = ToolRegistry {
        tool(SayToUser)
    }

    @Test
    fun test_singleSubgraph_createCheckpoint() = runTest {
        val checkpointId = "test-checkpoint"
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = checkpointSubgraphStrategy(checkpointId),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
            }
        }

        val output = agent.run("Start the test")

        assertEquals(
            "Start the test\n" +
                "Node 1 output\n" +
                "sg1 node output\n" +
                "Checkpoint Created\n" +
                "sg2 node output\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun test_singleSubgraph_createAndRollbackToCheckpoint() = runTest {
        val checkpointId = "test-checkpoint"
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = checkpointSubgraphWithRollbackStrategy(checkpointId),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
            }
        }

        val output = agent.run("Start the test")

        assertEquals(
            "History: You are a test agent.\n" +
                "Node 1 output\n" +
                "sg1 node output\n" +
                "Checkpoint created with ID: test-checkpoint\n" +
                "sg2 node output\n" +
                "Skipped rollback because it was already performed",
            output
        )
    }

    @Test
    fun test_nestedSubgraphs_createCheckpoint() = runTest {
        val checkpointId = "test-checkpoint"
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = nestedSubgraphCheckpointStrategy(checkpointId),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
            }
        }

        val output = agent.run("Start the test")

        assertEquals(
            "History: You are a test agent.\n" +
                "Node 1 output\n" +
                "sgNode1 node output\n" +
                "sg2Node1 node output\n" +
                "Checkpoint created with ID: test-checkpoint\n" +
                "sg2Node2 node output\n" +
                "sgNode2 node output",
            output
        )
    }

    @Test
    fun test_nestedSubgraphs_createAndRollbackToCheckpoint() = runTest {
        val checkpointId = "test-checkpoint"
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = nestedSubgraphCheckpointWithRollbackStrategy(checkpointId),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
            }
        }

        val output = agent.run("Start the test")

        assertEquals(
            "History: You are a test agent.\n" +
                "Node 1 output\n" +
                "sgNode1 node output\n" +
                "sg2Node1 node output\n" +
                "Checkpoint created with ID: test-checkpoint\n" +
                "sg2Node2 node output\n" +
                "Skipped rollback because it was already performed\n" +
                "sgNode2 node output",
            output
        )
    }

    @Test
    fun `test reusing subgraph with Persistence - clean start `() = runTest {
        val mockExecutor: PromptExecutor = getMockExecutor {}

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a test agent.")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 100
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategyWithRepeatedSubgraphs(),
            agentConfig = agentConfig,
        ) {
            // Install the AgentCheckpoint feature
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
            }
        }

        agent.run("Start the test")
    }

    @Test
    fun `test reusing subgraph with Persistence - checkpoint start`() = runTest {
        val mockExecutor: PromptExecutor = getMockExecutor {}
        val agentId = "test-agent"

        val inMemoryPersistence = InMemoryPersistenceStorageProvider()

        val checkpoint = AgentCheckpointData(
            checkpointId = "checkpoint-1",
            createdAt = Clock.System.now(),
            nodeId = "repeated-subgraphs-test:sg1:sgNode1",
            lastInput = JsonPrimitive("Input at checkpoint"),
            messageHistory = listOf(),
            version = 1L
        )

        inMemoryPersistence.saveCheckpoint(agentId, checkpoint)

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a test agent.")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 100
        )

        val agent = AIAgent(
            id = agentId,
            promptExecutor = mockExecutor,
            strategy = strategyWithRepeatedSubgraphs(),
            agentConfig = agentConfig
        ) {
            install(Persistence) {
                storage = inMemoryPersistence
            }
        }

        agent.run("Start the test")
    }
}
