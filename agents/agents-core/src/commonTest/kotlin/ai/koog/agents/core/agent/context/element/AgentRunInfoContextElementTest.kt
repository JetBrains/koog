package ai.koog.agents.core.agent.context.element

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

class AgentRunInfoContextElementTest {
    private val agentId = "test-agent"
    private val runId = "test-run"

    @Test
    fun testContextElementCreation() {
        val agentId = agentId
        val runId = runId
        val config = createTestConfig()
        val strategyName = "test-strategy"

        val element = AgentRunInfoContextElement(
            agentId = agentId,
            runId = runId,
            agentConfig = config,
            strategyName = strategyName
        )

        assertEquals(agentId, element.agentId)
        assertEquals(runId, element.runId)
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
            agentId = agentId,
            runId = runId,
            agentConfig = createTestConfig(),
            strategyName = "test-strategy"
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
            agentId = agentId,
            runId = runId,
            agentConfig = createTestConfig(),
            strategyName = "test-strategy"
        )

        withContext(element) {
            val retrievedElement = coroutineContext.getAgentRunInfoElementOrThrow()
            assertEquals(element, retrievedElement)
        }

        assertFailsWith<IllegalStateException> {
            coroutineContext.getAgentRunInfoElementOrThrow()
        }
    }


    private fun createTestConfig(): AIAgentConfigBase {
        return AIAgentConfig(
            prompt = createTestPrompt(),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10,
            missingToolsConversionStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        )
    }

    private fun createTestPrompt(): Prompt {
        return prompt("test-prompt") {}
    }
}