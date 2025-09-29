package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AIAgentServiceTest {

    private fun mockConfig(): AIAgentConfig = AIAgentConfig(
        prompt = prompt("test-prompt") { system("sys") },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 3
    )

    private fun mockGraphStrategy() = strategy<String, String>("mock") {
        edge(
            nodeStart forwardTo nodeFinish transformed { input ->
                "ok:$input"
            }
        )
    }

    private fun mockFunctionalStrategy(): AIAgentFunctionalStrategy<Int, Int> =
        functionalStrategy("plusOne") { input -> input + 1 }

    @Test
    fun testCompanionInvoke_graphWithTypes_buildsServiceAndCreatesAgents() = runTest {
        val executor = getMockExecutor { }
        val service = AIAgentService(
            promptExecutor = executor,
            agentConfig = mockConfig(),
            strategy = mockGraphStrategy(),
            toolRegistry = ToolRegistry {}
        )

        // basic properties propagated
        assertEquals(executor, service.promptExecutor)
        assertEquals(mockConfig().maxAgentIterations, service.agentConfig.maxAgentIterations)
        assertNotNull(service.toolRegistry)

        // create agent and run
        val agent = service.createAgent(id = "id-1", clock = Clock.System)
        val out = agent.run("in")
        assertEquals("ok:in", out)

        // managed agents tracking
        assertEquals(1, service.listAllAgents().size)
        assertEquals(agent, service.agentById("id-1"))
        assertTrue(
            service.listInactiveAgents().isNotEmpty()
        ) // before run, agent has finished immediately, not running now
    }

    @Test
    fun testCompanionInvoke_graphWithModel_buildsServiceFromModel() = runTest {
        val executor = getMockExecutor { }
        val service = AIAgentService(
            promptExecutor = executor,
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            strategy = mockGraphStrategy(),
            toolRegistry = ToolRegistry {},
            systemPrompt = "You are helpful",
            temperature = 0.5,
            numberOfChoices = 2,
            maxIterations = 7
        )
        assertEquals(executor, service.promptExecutor)
        assertEquals(7, service.agentConfig.maxAgentIterations)
        assertEquals(OllamaModels.Meta.LLAMA_3_2, service.agentConfig.model)
    }

    @Test
    fun testFunctionalService_factoryAndRun() = runTest {
        val executor = getMockExecutor { }
        val cfg = mockConfig()
        val service = AIAgentService(
            promptExecutor = executor,
            agentConfig = cfg,
            strategy = mockFunctionalStrategy(),
            toolRegistry = ToolRegistry {}
        )

        val agent = service.createAgent()
        val out = agent.run(41)
        assertEquals(42, out)

        // management API
        assertEquals(1, service.listAllAgents().size)
        assertEquals(1, service.listInactiveAgents().size)
        assertEquals(0, service.listActiveAgents().size)
        assertEquals(1, service.listFinishedAgents().size)

        // remove operations
        assertTrue(service.removeAgent(agent))
        assertFalse(service.removeAgentWithId("no-such"))
        assertEquals(0, service.listAllAgents().size)
    }

    @Test
    fun testFromAgent_factories() = runTest {
        val executor = getMockExecutor { }
        val cfg = mockConfig()
        val strat = mockGraphStrategy()
        val graphAgent = GraphAIAgent(
            id = "graph-id",
            strategy = strat,
            promptExecutor = executor,
            agentConfig = cfg,
            inputType = typeOf<String>(),
            outputType = typeOf<String>()
        )

        val serviceFromGraph = AIAgentService.fromAgent<String, String>(graphAgent)
        assertEquals(executor, serviceFromGraph.promptExecutor)
        assertEquals(cfg, serviceFromGraph.agentConfig)

        val funcService = AIAgentService(
            promptExecutor = executor,
            agentConfig = cfg,
            strategy = mockFunctionalStrategy(),
            toolRegistry = ToolRegistry {}
        )
        val funcAgent = funcService.createAgent()
        val serviceFromFunctional = AIAgentService.fromAgent(
            funcAgent as FunctionalAIAgent<Int, Int>
        )
        assertEquals(executor, (serviceFromFunctional as FunctionalAIAgentService<Int, Int>).promptExecutor)
        assertEquals(cfg, serviceFromFunctional.agentConfig)
    }

    @Test
    fun testCreateAgentAndRun_andCloseAll() = runTest {
        val executor = getMockExecutor { }
        val service = AIAgentService(
            promptExecutor = executor,
            agentConfig = mockConfig(),
            strategy = mockGraphStrategy()
        )

        val result = service.createAgentAndRun("x")
        assertEquals("ok:x", result)

        // Create a couple agents then closeAll should not throw
        service.createAgent("a")
        service.createAgent("b")
        assertEquals(3, service.listAllAgents().size)
        service.closeAll() // should call close() on all without error
    }
}
