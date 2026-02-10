package ai.koog.agents.core.optimization

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.OptimizationConfig
import ai.koog.agents.core.optimization.util.findOptimizableModules
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
 * 3. Strategy utils find optimizable nodes
 * 4. OptimizableNode properties are set correctly
 */
class OptimizationInfrastructureTest {

    /**
     * Test that findOptimizableModules() discovers OptimizableNode instances
     * and ignores regular nodes.
     */
    @Test
    fun testFindOptimizableModules() {
        val testStrategy = strategy("test") {
            val optimizable by optimizableNode(
                instruction = "This node is optimizable",
            )

            val notOptimizable by node<String, String> { input ->
                input.lowercase()
            }

            edge(nodeStart forwardTo optimizable)
            edge(optimizable forwardTo notOptimizable)
            edge(notOptimizable forwardTo nodeFinish)
        }

        val modules = testStrategy.findOptimizableModules()

        assertEquals(1, modules.size)
        assertEquals("optimizable", modules[0].name)
        assertEquals("This node is optimizable", modules[0].instruction)
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
     * Test that OptimizableNode properties are set correctly via DSL.
     */
    @Test
    fun testOptimizableNodePropertiesViaDelegate() {
        val testStrategy = strategy("test") {
            val myNode by optimizableNode(
                instruction = "My instruction",
                description = "My description",
                demonstrations = listOf(Demonstration("example in", "example out")),
            )

            edge(nodeStart forwardTo myNode)
            edge(myNode forwardTo nodeFinish)
        }

        val modules = testStrategy.findOptimizableModules()
        assertEquals(1, modules.size)

        val module = modules[0]
        assertEquals("myNode", module.name)
        assertEquals("My instruction", module.instruction)
        assertEquals("My description", module.description)
        assertEquals(1, module.demonstrations.size)
        assertEquals("example in", module.demonstrations[0].input)
        assertEquals("example out", module.demonstrations[0].output)
    }

    /**
     * Test that optimizableNode works with nullable inputField/outputField.
     */
    @Test
    fun testOptimizableNodeNullableFields() {
        val testStrategy = strategy("test") {
            val myNode by optimizableNode(
                instruction = "My instruction",
                demonstrations = listOf(Demonstration("a", "b")),
            )

            edge(nodeStart forwardTo myNode)
            edge(myNode forwardTo nodeFinish)
        }

        val modules = testStrategy.findOptimizableModules()
        assertEquals(1, modules.size)

        val module = modules[0]
        assertEquals(1, module.demonstrations.size)
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
     * This mimics what OptimizableNode does internally.
     */
    @Test
    fun testNodeLambdaPattern() = runBlocking {
        val config = OptimizationConfig(instructions = mapOf("myNode" to "optimized instruction"))

        assertEquals("optimized instruction", config.getInstruction("myNode"))

        withContext(config) {
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
