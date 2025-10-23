package ai.koog.agents.core.agent.context.element

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.reflect.typeOf
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
        val nodeInput = "test-input"
        val nodeInputType = typeOf<String>()

        val element = NodeInfoContextElement(nodeName = nodeName, input = nodeInput, inputType = nodeInputType)

        assertEquals(nodeName, element.nodeName)
        assertEquals(NodeInfoContextElement.Key, element.key)
        assertEquals(nodeInput, element.input)
        assertEquals(nodeInputType, element.inputType)
    }

    @Test
    fun testContextElementEquality() {
        val element1 = NodeInfoContextElement(nodeName = "node1", input = "input1", inputType = typeOf<String>())
        val element2 = NodeInfoContextElement(nodeName = "node1", input = "input1", inputType = typeOf<String>())
        val element3 = NodeInfoContextElement(nodeName = "node2", input = "input2", inputType = typeOf<String>())

        assertEquals(element1, element2)
        assertEquals(element1.hashCode(), element2.hashCode())
        assertNotEquals(element1, element3)
    }

    @Test
    fun testGetNodeInfoElement() = runTest {
        val element = NodeInfoContextElement(nodeName = nodeName, input = "input", inputType = typeOf<String>())

        // Test with an element in context
        withContext(element) {
            val retrievedElement = getNodeInfoElement()
            assertNotNull(retrievedElement)
            assertEquals(element, retrievedElement)
        }

        // Test with no element in context
        val retrievedElement = getNodeInfoElement()
        assertNull(retrievedElement)
    }

    @Test
    fun testMultipleElementsInContext() = runTest {
        val nodeElement = NodeInfoContextElement(nodeName = nodeName, input = "input", inputType = typeOf<String>())
        val testPrompt = prompt("test-prompt") {}
        val testModel = OllamaModels.Meta.LLAMA_3_2

        val agentElement = AgentRunInfoContextElement(
            agentId = "test-agent",
            runId = "test-run",
            agentConfig = object : AIAgentConfigBase {
                override val prompt: Prompt = testPrompt
                override val model: LLModel = testModel
            },
            strategyName = "test-strategy"
        )

        withContext(nodeElement + agentElement) {
            val retrievedNodeElement = getNodeInfoElement()
            val retrievedAgentElement = coroutineContext[AgentRunInfoContextElement.Key]

            assertEquals(nodeElement, retrievedNodeElement)
            assertEquals(agentElement, retrievedAgentElement)
        }
    }
}
