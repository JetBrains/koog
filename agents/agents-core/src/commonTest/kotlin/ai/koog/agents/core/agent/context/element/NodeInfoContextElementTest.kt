package ai.koog.agents.core.agent.context.element

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NodeInfoContextElementTest {
    private val nodeName = "test-node"

    @Test
    fun testContextElementCreation() {
        val nodeName = nodeName

        val element = NodeInfoContextElement(nodeName = nodeName)

        assertEquals(nodeName, element.nodeName)
        assertEquals(NodeInfoContextElement.Key, element.key)
    }

    @Test
    fun testContextElementEquality() {
        val element1 = NodeInfoContextElement(nodeName = "node1")
        val element2 = NodeInfoContextElement(nodeName = "node1")
        val element3 = NodeInfoContextElement(nodeName = "node2")

        assertEquals(element1, element2)
        assertEquals(element1.hashCode(), element2.hashCode())
        assertNotEquals(element1, element3)
    }

    @Test
    fun testGetNodeInfoElement() = runTest {
        val element = NodeInfoContextElement(nodeName = nodeName)

        // Test with element in context
        withContext(element) {
            val retrievedElement = coroutineContext.getNodeInfoElement()
            assertNotNull(retrievedElement)
            assertEquals(element, retrievedElement)
        }

        // Test with no element in context
        val retrievedElement = coroutineContext.getNodeInfoElement()
        assertNull(retrievedElement)
    }

    @Test
    fun testMultipleElementsInContext() = runTest {
        val nodeElement = NodeInfoContextElement(nodeName = nodeName)
        val testPrompt = prompt("test-prompt") {}
        val testModel = OllamaModels.Meta.LLAMA_3_2
        val testStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)

        val agentElement = AgentRunInfoContextElement(
            agentId = "test-agent",
            runId = "test-run",
            agentConfig = object : AIAgentConfigBase {
                override val prompt: Prompt = testPrompt
                override val model: LLModel = testModel
                override val maxAgentIterations: Int = 10
                override val missingToolsConversionStrategy: MissingToolsConversionStrategy = testStrategy
            },
            strategyName = "test-strategy"
        )

        withContext(nodeElement + agentElement) {
            val retrievedNodeElement = coroutineContext.getNodeInfoElement()
            val retrievedAgentElement = coroutineContext[AgentRunInfoContextElement.Key]

            assertEquals(nodeElement, retrievedNodeElement)
            assertEquals(agentElement, retrievedAgentElement)
        }
    }
}
