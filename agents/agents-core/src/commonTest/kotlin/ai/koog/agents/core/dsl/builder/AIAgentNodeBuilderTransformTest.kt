package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AIAgentNodeBuilderTransformTest {

    private fun createMockExecutor() = getMockExecutor {
    }

    private fun createToolRegistry() = ToolRegistry {
        tool(DummyTool())
    }

    private fun createBaseAgentConfig(): AIAgentConfig {
        return AIAgentConfig(
            prompt = prompt("test-agent") { user("test prompt") },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )
    }

    @Test
    fun testTransformBasicStringToInt() = runTest {
        val strategy = strategy<String, Int>("strategy") {
            val transformedNode by node<String, String>("node") { _ ->
                "123"
            }.transform { it.toInt() }

            edge(nodeStart forwardTo transformedNode)
            edge(transformedNode forwardTo nodeFinish)
        }

        val runner = AIAgent(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = createBaseAgentConfig(),
            toolRegistry = createToolRegistry()
        )

        val result = runner.run("input")
        assertEquals(123, result)
    }

    @Test
    fun testTransformChaining() = runTest {
        val strategy = strategy<String, Double>("strategy") {
            val chainedNode by node<String, String>("chainedNode") { _ ->
                "42"
            }.transform { it.toInt() }
                .transform { it * 2 }
                .transform { it.toDouble() }

            edge(nodeStart forwardTo chainedNode)
            edge(chainedNode forwardTo nodeFinish)
        }

        val runner = AIAgent(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = createBaseAgentConfig(),
            toolRegistry = createToolRegistry()
        )

        val result = runner.run("input")
        assertEquals(84.0, result)
    }

    @Test
    fun testReuseNodeWithTransform() = runTest {
        val strategy = strategy<String, String>("strategy") {
            val doubleNode by node<String, String>("doubleNode") { input ->
                input
            }.transform { "$it $it" }

            val helloNode by node<String, String>("helloNode") { input ->
                "Hello, $input!"
            }

            edge(nodeStart forwardTo doubleNode)
            edge(doubleNode forwardTo helloNode onCondition { it.length < 15 })
            edge(helloNode forwardTo doubleNode)
            edge(doubleNode forwardTo nodeFinish onCondition { it.length >= 15 })
        }

        val runner = AIAgent(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = createBaseAgentConfig(),
            toolRegistry = createToolRegistry()
        )

        val result = runner.run("Maria")
        assertEquals("Hello, Maria Maria! Hello, Maria Maria!", result)
    }
}
