package ai.koog.agents.core.agent.context.element

import ai.koog.agents.core.agent.context.AgentTestBase
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class AgentRunInfoContextElementTest : AgentTestBase() {

    @Test
    fun testContextElementCreation() {
        val config = createTestConfig()

        val element = AgentRunInfoContextElement(
            agentId = testAgentId,
            runId = testRunId,
            agentConfig = config,
            strategyName = strategyName
        )

        assertEquals(testAgentId, element.agentId)
        assertEquals(testRunId, element.runId)
        assertEquals(config, element.agentConfig)
        assertEquals(strategyName, element.strategyName)
        assertEquals(AgentRunInfoContextElement.Key, element.key)
    }

    @Test
    fun testContextElementEquality() {
        val sharedConfig = createTestConfig()

        val element1 = AgentRunInfoContextElement(
            agentId = "agent1",
            runId = "run1",
            agentConfig = sharedConfig,
            strategyName = "strategy1"
        )

        val element2 = AgentRunInfoContextElement(
            agentId = "agent1",
            runId = "run1",
            agentConfig = sharedConfig,
            strategyName = "strategy1"
        )

        val element3 = AgentRunInfoContextElement(
            agentId = "agent2",
            runId = "run2",
            agentConfig = createTestConfig(),
            strategyName = "strategy2"
        )

        assertEquals(element1, element2)
        assertEquals(element1.hashCode(), element2.hashCode())
        assertNotEquals(element1, element3)
    }

    @Test
    fun testGetElementFromContext() = runTest {
        val element = AgentRunInfoContextElement(
            agentId = testAgentId,
            runId = testRunId,
            agentConfig = createTestConfig(),
            strategyName = strategyName
        )

        val context = withContext(element) {
            val retrievedElement = coroutineContext[AgentRunInfoContextElement.Key]

            assertNotNull(retrievedElement)
            assertEquals(element, retrievedElement)

            coroutineContext
        }

        // Verify the element is in the returned context
        val retrievedElement = context[AgentRunInfoContextElement.Key]
        assertNotNull(retrievedElement)
        assertEquals(element, retrievedElement)
    }

    @Test
    fun testGetElementOrThrow() = runTest {
        val element = AgentRunInfoContextElement(
            agentId = testAgentId,
            runId = testRunId,
            agentConfig = createTestConfig(),
            strategyName = strategyName,
        )

        withContext(element) {
            val retrievedElement = coroutineContext.getAgentRunInfoElementOrThrow()
            assertEquals(element, retrievedElement)
        }

        assertFailsWith<IllegalStateException> {
            coroutineContext.getAgentRunInfoElementOrThrow()
        }
    }
}
