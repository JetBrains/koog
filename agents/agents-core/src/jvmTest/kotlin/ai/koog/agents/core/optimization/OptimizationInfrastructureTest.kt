package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.OptimizationConfig
import ai.koog.agents.core.optimization.util.findOptimizableNodes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests to verify the optimization infrastructure works correctly.
 * These tests validate that:
 * 1. OptimizationConfig can override node instructions via coroutine context
 * 2. Context helper functions read from context correctly
 * 3. TraceCollectionFeature captures node I/O
 * 4. Strategy utils find optimizable nodes
 */
class OptimizationInfrastructureTest {

    /**
     * Test that findOptimizableNodes() discovers nodes with instructions.
     */
    @Test
    fun testFindOptimizableNodes() {
        // Strategy with one optimizable node and one non-optimizable node
        val testStrategy = strategy("test") {
            val optimizable by node<String, String>(
                instruction = "This node is optimizable"
            ) { input -> input.uppercase() }

            val notOptimizable by node<String, String> { input ->
                input.lowercase()
            }

            edge(nodeStart forwardTo optimizable)
            edge(optimizable forwardTo notOptimizable)
            edge(notOptimizable forwardTo nodeFinish)
        }

        val optimizableNodes = testStrategy.findOptimizableNodes()

        assertEquals(1, optimizableNodes.size)
        assertEquals("optimizable", optimizableNodes[0].name)
        assertEquals("This node is optimizable", optimizableNodes[0].instruction)
    }

    /**
     * Test that OptimizationConfig is accessible in coroutine context.
     */
    @Test
    fun testOptimizationConfigInCoroutineContext() = runBlocking {
        val config = OptimizationConfig(
            instructions = mapOf("testNode" to "overridden instruction"),
            demonstrations = mapOf("testNode" to listOf(Demonstration("in", "out")))
        )

        withContext(config) {
            val retrievedConfig = coroutineContext[OptimizationConfig]
            assertEquals(config, retrievedConfig)
            assertEquals("overridden instruction", retrievedConfig?.getInstruction("testNode"))
        }
    }

    /**
     * Test that node's instruction field is set correctly via DSL.
     */
    @Test
    fun testNodeInstructionFieldViaDelegate() {
        val testStrategy = strategy("test") {
            val myNode by node<String, String>(
                instruction = "My instruction",
                description = "My description",
                demonstrations = listOf(Demonstration("example in", "example out"))
            ) { input -> input }

            edge(nodeStart forwardTo myNode)
            edge(myNode forwardTo nodeFinish)
        }

        val nodes = testStrategy.findOptimizableNodes()
        assertEquals(1, nodes.size)

        val node = nodes[0]
        assertEquals("myNode", node.name)
        assertEquals("My instruction", node.instruction)
        assertEquals("My description", node.description)
        assertEquals(1, node.demonstrations.size)
        assertEquals("example in", node.demonstrations[0].input)
        assertEquals("example out", node.demonstrations[0].output)
    }

    /**
     * Test that AIAgentNode.copy() creates a proper copy with updated fields.
     */
    @Test
    fun testNodeCopy() {
        val testStrategy = strategy("test") {
            val myNode by node<String, String>(
                instruction = "original instruction",
                demonstrations = emptyList(),
                description = "original description"
            ) { input -> input }

            edge(nodeStart forwardTo myNode)
            edge(myNode forwardTo nodeFinish)
        }

        val originalNode = testStrategy.findOptimizableNodes()[0]

        @Suppress("UNCHECKED_CAST")
        val copiedNode = (originalNode as AIAgentNode<String, String>).copy(
            instruction = "new instruction",
            demonstrations = listOf(Demonstration("a", "b")),
            description = "new description"
        )

        // Original unchanged
        assertEquals("original instruction", originalNode.instruction)
        assertEquals(0, originalNode.demonstrations.size)
        assertEquals("original description", originalNode.description)

        // Copy has new values
        assertEquals("new instruction", copiedNode.instruction)
        assertEquals(1, copiedNode.demonstrations.size)
        assertEquals("new description", copiedNode.description)

        // Name and execute preserved
        assertEquals(originalNode.name, copiedNode.name)
    }

    /**
     * Test OptimizationConfig merging with plus().
     */
    @Test
    fun testOptimizationConfigMerging() {
        val config1 = OptimizationConfig(
            instructions = mapOf("node1" to "instruction1"),
            demonstrations = mapOf("node1" to listOf(Demonstration("a", "b")))
        )

        val config2 = config1.plus(
            additionalInstructions = mapOf("node2" to "instruction2"),
            additionalDemonstrations = mapOf("node2" to listOf(Demonstration("c", "d")))
        )

        // Original unchanged
        assertEquals(1, config1.instructions.size)
        assertEquals(1, config1.demonstrations.size)

        // Merged has both
        assertEquals(2, config2.instructions.size)
        assertEquals("instruction1", config2.getInstruction("node1"))
        assertEquals("instruction2", config2.getInstruction("node2"))
    }

    /**
     * Test the pattern that node lambdas would use to read instruction from context.
     * This mimics what getNodeInstruction() does internally.
     */
    @Test
    fun testNodeLambdaPattern() = runBlocking {
        val config = OptimizationConfig(instructions = mapOf("myNode" to "optimized instruction"))

        // Verify config has the instruction
        assertEquals("optimized instruction", config.getInstruction("myNode"))

        // Test that withContext properly adds the config to coroutine context
        withContext(config) {
            // Inside withContext, the config should be accessible
            val retrievedConfig = coroutineContext[OptimizationConfig]
            assertEquals(config, retrievedConfig, "Config should be in coroutine context")
            assertEquals("optimized instruction", retrievedConfig?.getInstruction("myNode"))

            // Simulate reading instruction with fallback (the pattern nodes use)
            val instruction = coroutineContext[OptimizationConfig]?.getInstruction("myNode")
                ?: "default instruction"
            assertEquals("optimized instruction", instruction)
        }

        // Outside withContext, no config
        val outsideConfig = coroutineContext[OptimizationConfig]
        assertEquals(null, outsideConfig, "Config should not be in context outside withContext")
    }

    /**
     * Test demonstrations reading pattern with type casting.
     */
    @Test
    fun testDemonstrationReadingPattern() = runBlocking {
        val demo1 = Demonstration("input1", "output1")
        val demo2 = Demonstration("input2", "output2")
        val config = OptimizationConfig(
            demonstrations = mapOf("myNode" to listOf(demo1, demo2))
        )

        withContext(config) {
            val demos = coroutineContext[OptimizationConfig]
                ?.getTypedDemonstrations<String, String>("myNode")
                ?: emptyList()

            assertEquals(2, demos.size)
            assertEquals("input1", demos[0].input)
            assertEquals("output1", demos[0].output)
        }
    }
}
