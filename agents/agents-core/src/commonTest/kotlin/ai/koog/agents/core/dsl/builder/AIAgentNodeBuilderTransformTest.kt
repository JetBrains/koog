package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.testing.tools.AIAgentContextMockBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AIAgentNodeBuilderTransformTest {

    private fun createMockContext(): AIAgentContextBase {
        return AIAgentContextMockBuilder().apply {
            runId = "test-run-id"
            strategyName = "test-strategy"
        }.build()
    }

    @Test
    fun testTransformBasicStringToInt() = runTest {
        val strategy = strategy<String, String>("strategy") {
            val node by node<String, String>("node") { input ->
                "Result: $input"
            }.transform { it.toInt() }

            TODO()
        }
    }
}
