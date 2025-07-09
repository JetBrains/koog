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
import kotlin.test.*

class NodeInfoContextElementTest {

    @Test
    fun testContextElementCreation() {
        val nodeName = "test-node"

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
    fun testGetElementFromContext() = runTest {
        val element = NodeInfoContextElement(nodeName = "test-node")

        // Create a coroutine context with the element
        val context = withContext(element) {
            // Get the element from the current coroutine context
            val retrievedElement = coroutineContext[NodeInfoContextElement.Key]

            assertNotNull(retrievedElement)
            assertEquals(element, retrievedElement)

            coroutineContext
        }

        // Verify the element is in the returned context
        val retrievedElement = context[NodeInfoContextElement.Key]
        assertNotNull(retrievedElement)
        assertEquals(element, retrievedElement)
    }

    @Test
    fun testGetElementFromEmptyContext() = runTest {
        // Try to get the element from an empty coroutine context
        val retrievedElement = coroutineContext[NodeInfoContextElement.Key]

        // Verify the element is not found
        assertNull(retrievedElement)
    }

    @Test
    fun testGetNodeInfoElement() = runTest {
        val element = NodeInfoContextElement(nodeName = "test-node")

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
        val nodeElement = NodeInfoContextElement(nodeName = "test-node")
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

        // Create a coroutine context with both elements
        withContext(nodeElement + agentElement) {
            // Get both elements from the context
            val retrievedNodeElement = coroutineContext.getNodeInfoElement()
            val retrievedAgentElement = coroutineContext[AgentRunInfoContextElement.Key]

            // Verify both elements are found
            assertNotNull(retrievedNodeElement)
            assertNotNull(retrievedAgentElement)
            assertEquals(nodeElement, retrievedNodeElement)
            assertEquals(agentElement, retrievedAgentElement)
        }
    }
}