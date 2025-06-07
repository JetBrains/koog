package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.utils.Some
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AIAgentNodeTest {

    @Test
    fun testAddEdge() = runTest {
        // Create source and destination nodes
        val sourceNode = TestNode<String, String>("sourceNode")
        val destinationNode = TestNode<String, String>("destinationNode")

        // Create an edge
        val edge = AIAgentEdge<String, String>(
            toNode = destinationNode,
            forwardOutput = { _, output -> Some(output) }
        )

        // Add the edge to the source node
        sourceNode.addEdge(edge)

        // Verify the edge was added
        assertEquals(1, sourceNode.edges.size, "Edge should be added to the node")
        assertSame(edge, sourceNode.edges[0], "The added edge should be in the edges list")
        assertEquals(destinationNode.name, sourceNode.edges[0].toNode.name, "The edge should point to the destination node")
    }

    @Test
    fun testReplaceEdge() = runTest {
        // Create source and destination nodes
        val sourceNode = TestNode<String, String>("sourceNode")
        val destinationNode = TestNode<String, String>("destinationNode")

        // Create first edge
        val edge1 = AIAgentEdge<String, String>(
            toNode = destinationNode,
            forwardOutput = { _, output -> Some("First: $output") }
        )

        // Create second edge with the same destination node
        val edge2 = AIAgentEdge<String, String>(
            toNode = destinationNode,
            forwardOutput = { _, output -> Some("Second: $output") }
        )

        // Add both edges to the source node
        sourceNode.addEdge(edge1)
        sourceNode.addEdge(edge2)

        // Verify only the second edge remains
        assertEquals(1, sourceNode.edges.size, "Only one edge should remain")
        assertSame(edge2, sourceNode.edges[0], "The second edge should replace the first edge")

        // Test the edge's transformation
        val context = MockAIAgentContext()
        val result = sourceNode.edges[0].forwardOutputUnsafe("test", context)
        assertEquals("Second: test", result.value, "The second edge's transformation should be applied")
    }

    @Test
    fun testAddMultipleEdgesToDifferentNodes() = runTest {
        // Create source and destination nodes
        val sourceNode = TestNode<String, String>("sourceNode")
        val destinationNode1 = TestNode<String, String>("destinationNode1")
        val destinationNode2 = TestNode<String, String>("destinationNode2")

        // Create edges to different destination nodes
        val edge1 = AIAgentEdge<String, String>(
            toNode = destinationNode1,
            forwardOutput = { _, output -> Some(output) }
        )

        val edge2 = AIAgentEdge<String, String>(
            toNode = destinationNode2,
            forwardOutput = { _, output -> Some(output) }
        )

        // Add both edges to the source node
        sourceNode.addEdge(edge1)
        sourceNode.addEdge(edge2)

        // Verify both edges were added
        assertEquals(2, sourceNode.edges.size, "Both edges should be added")
        assertTrue(sourceNode.edges.contains(edge1), "Edge to destinationNode1 should be in the edges list")
        assertTrue(sourceNode.edges.contains(edge2), "Edge to destinationNode2 should be in the edges list")
    }

    // Test implementation of AIAgentNodeBase for testing
    private class TestNode<Input, Output>(override val name: String) : AIAgentNodeBase<Input, Output>() {
        override suspend fun execute(context: AIAgentContextBase, input: Input): Output {
            throw NotImplementedError("Not implemented for test")
        }
    }

    // Mock implementation of AIAgentContextBase for testing
    @OptIn(InternalAgentsApi::class)
    private class MockAIAgentContext : AIAgentContextBase {
        override val pipeline: AIAgentPipeline = AIAgentPipeline()

        override val environment: AIAgentEnvironment
            get() = throw NotImplementedError("Not implemented for test")

        override val agentInput: String
            get() = "test input"

        override val config: AIAgentConfigBase
            get() = throw NotImplementedError("Not implemented for test")

        override val llm: AIAgentLLMContext
            get() = throw NotImplementedError("Not implemented for test")

        override val stateManager: AIAgentStateManager
            get() = throw NotImplementedError("Not implemented for test")

        override val storage: AIAgentStorage
            get() = throw NotImplementedError("Not implemented for test")

        override val sessionUuid: Uuid
            get() = Uuid.parse("00000000-0000-0000-0000-000000000000")

        override val strategyId: String
            get() = "test-strategy"

        override fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature? = null

        override fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature? = null

        override fun copy(
            environment: AIAgentEnvironment?,
            agentInput: String?,
            config: AIAgentConfigBase?,
            llm: AIAgentLLMContext?,
            stateManager: AIAgentStateManager?,
            storage: AIAgentStorage?,
            sessionUuid: Uuid?,
            strategyId: String?,
            pipeline: AIAgentPipeline?
        ): AIAgentContextBase = this
    }
}
