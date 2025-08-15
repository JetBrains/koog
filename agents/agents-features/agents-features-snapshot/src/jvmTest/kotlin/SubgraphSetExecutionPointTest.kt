import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SubgraphSetExecutionPointTest {
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

    @Test
    fun test_singleSubgraph_teleportForward() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createSimpleTeleportSubgraphStrategy("Node2"),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("testAgentId")
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "Start the test\n" +
                "Node 1 output\n" +
                "sg1 node output\n" +
                "Teleported\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun test_singleSubgraph_teleportBackwards() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createSimpleTeleportSubgraphStrategy("Node1"),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("testAgentId")
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "Start the test\n" +
                "Node 1 output\n" +
                "sg1 node output\n" +
                "Teleported\n" +
                "Node 1 output\n" +
                "sg1 node output\n" +
                "Already teleported, passing by\n" +
                "sg2 node output\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun test_singleSubgraph_teleportInsideSubgraph_teleportForward() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createSimpleTeleportSubgraphStrategy("sgNode2"),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("testAgentId")
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "Start the test\n" +
                "Node 1 output\n" +
                "sg1 node output\n" +
                "Teleported\n" +
                "sg2 node output\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun test_singleSubgraph_teleportInsideSubgraph_teleportBackwards() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createSimpleTeleportSubgraphStrategy("sgNode1"),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("testAgentId")
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "Start the test\n" +
                "Node 1 output\n" +
                "sg1 node output\n" +
                "Teleported\n" +
                "sg1 node output\n" +
                "Already teleported, passing by\n" +
                "sg2 node output\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun test_innerSubgraphs_teleportToOuterSubgraphForward() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createSimpleTeleportSubgraphWithInnerSubgraph("sgNode2"),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("testAgentId")
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "Start the test\n" +
                "Node 1 output\n" +
                "sgNode1 node output\n" +
                "sg2Node1 node output\n" +
                "sg2Node2 node output\n" +
                "Teleported\n" +
                "sgNode2 node output\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun test_innerSubgraphs_teleportToOuterSubgraphBackwards() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createSimpleTeleportSubgraphWithInnerSubgraph("sgNode1"),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("testAgentId")
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "Start the test\n" +
                "Node 1 output\n" +
                "sgNode1 node output\n" +
                "sg2Node1 node output\n" +
                "sg2Node2 node output\n" +
                "Teleported\n" +
                "sgNode1 node output\n" +
                "sg2Node1 node output\n" +
                "sg2Node2 node output\n" +
                "Already teleported, passing by\n" +
                "sgNode2 node output\n" +
                "Node 2 output",
            output
        )
    }
}
