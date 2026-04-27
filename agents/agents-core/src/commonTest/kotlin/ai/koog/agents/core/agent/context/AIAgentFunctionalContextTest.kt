package ai.koog.agents.core.agent.context

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class AIAgentFunctionalContextTest : AgentTestBase() {

    @Test
    fun testForkCreatesIndependentStorageSnapshot() = runTest {
        val key = AIAgentStorageKey<String>("test-key")
        val context = createTestFunctionalContext()
        context.storage.set(key, "original-value")

        val fork = context.fork()
        fork.storage.set(key, "fork-value")

        assertNotSame(context.storage, fork.storage)
        assertEquals("original-value", context.storage.get(key))
        assertEquals("fork-value", fork.storage.get(key))
    }

    @Test
    fun testForkCreatesIndependentStateManagerSnapshot() = runTest {
        val context = createTestFunctionalContext()
        context.stateManager.withStateLock { state ->
            state.iterations = 1
        }

        val fork = context.fork()
        fork.stateManager.withStateLock { state ->
            state.iterations = 7
        }

        assertNotSame(context.stateManager, fork.stateManager)
        assertEquals(1, context.stateManager.withStateLock { it.iterations })
        assertEquals(7, fork.stateManager.withStateLock { it.iterations })
    }

    @Test
    fun testForkCreatesIndependentLLMContextSnapshot() = runTest {
        val context = createTestFunctionalContext()
        context.llm.writeSession {
            appendPrompt {
                user("original-message")
            }
        }

        val fork = context.fork()
        fork.llm.writeSession {
            appendPrompt {
                user("fork-message")
            }
        }

        assertNotSame(context.llm, fork.llm)
        assertEquals(listOf("original-message"), context.getHistory().map { it.content })
        assertEquals(listOf("original-message", "fork-message"), fork.getHistory().map { it.content })
    }

    private fun createTestFunctionalContext(): AIAgentFunctionalContext {
        val config = createTestConfig()

        return AIAgentFunctionalContext(
            environment = createTestEnvironment(),
            agentId = testAgentId,
            runId = "test-run-id",
            agentInput = "test-input",
            config = config,
            llm = createTestLLMContext(),
            stateManager = createTestStateManager(),
            storage = createTestStorage(),
            strategyName = strategyName,
            pipeline = AIAgentFunctionalPipeline(config, testClock),
            executionInfo = AgentExecutionInfo(null, testAgentId),
            parentContext = null
        )
    }
}
