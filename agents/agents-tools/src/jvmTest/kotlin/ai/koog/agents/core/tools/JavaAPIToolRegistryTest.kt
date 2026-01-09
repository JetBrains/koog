package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.core.tools.serialization.ToolJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for @JavaAPI methods in ToolRegistry class.
 * These tests verify that Java-facing API methods work correctly.
 */
@OptIn(InternalAgentToolsApi::class)
class JavaAPIToolRegistryTest {

    // Simple test tool classes
    class CalculatorTools : ToolSet {
        @Tool
        @LLMDescription("Adds two numbers")
        fun add(
            @LLMDescription("First number") a: Int,
            @LLMDescription("Second number") b: Int
        ): Int = a + b

        @Tool
        @LLMDescription("Multiplies two numbers")
        fun multiply(
            @LLMDescription("First number") a: Int,
            @LLMDescription("Second number") b: Int
        ): Int = a * b
    }

    class StringTools : ToolSet {
        @Tool
        @LLMDescription("Concatenates two strings")
        fun concat(
            @LLMDescription("First string") a: String,
            @LLMDescription("Second string") b: String
        ): String = a + b
    }

    @Test
    fun testBuilderMethodCreatesBuilder() {
        // Test that ToolRegistry.builder() creates a Builder instance
        val builder = ToolRegistry.builder()
        assertNotNull(builder)
    }

    @Test
    fun testBuilderToolMethod() = runTest {
        // Test that Builder.tool() method adds a tool
        val calculatorTools = CalculatorTools()
        val tools = calculatorTools.asTools(ToolJson)
        val addTool = tools.first { it.descriptor.name == "add" }

        val registry = ToolRegistry.builder()
            .tool(addTool)
            .build()

        assertEquals(1, registry.tools.size)
        assertEquals("add", registry.tools[0].name)
    }

    @Test
    fun testBuilderToolsMethod() = runTest {
        // Test that Builder.tools() method adds multiple tools
        val calculatorTools = CalculatorTools()
        val toolsList = calculatorTools.asTools(ToolJson)

        val registry = ToolRegistry.builder()
            .tools(toolsList)
            .build()

        assertEquals(2, registry.tools.size)
        assertTrue(registry.tools.any { it.name == "add" })
        assertTrue(registry.tools.any { it.name == "multiply" })
    }

    @Test
    fun testBuilderBuildMethod() = runTest {
        // Test that Builder.build() creates a ToolRegistry
        val calculatorTools = CalculatorTools()
        val tools = calculatorTools.asTools(ToolJson)

        val registry = ToolRegistry.builder()
            .tools(tools)
            .build()

        assertNotNull(registry)
        assertTrue(registry is ToolRegistry)
        assertEquals(2, registry.tools.size)
    }

    @Test
    fun testBuilderChainedCalls() = runTest {
        // Test that Builder methods can be chained
        val calculatorTools = CalculatorTools()
        val stringTools = StringTools()

        val calcTools = calculatorTools.asTools(ToolJson)
        val strTools = stringTools.asTools(ToolJson)

        val registry = ToolRegistry.builder()
            .tool(calcTools[0])
            .tool(calcTools[1])
            .tools(strTools)
            .build()

        assertEquals(3, registry.tools.size)
        assertTrue(registry.tools.any { it.name == "add" })
        assertTrue(registry.tools.any { it.name == "multiply" })
        assertTrue(registry.tools.any { it.name == "concat" })
    }

    @Test
    fun testBuilderToolsCanBeRetrieved() = runTest {
        // Test that tools added via Builder can be retrieved
        val calculatorTools = CalculatorTools()
        val tools = calculatorTools.asTools(ToolJson)

        val registry = ToolRegistry.builder()
            .tools(tools)
            .build()

        val addTool = registry.getTool("add")
        assertNotNull(addTool)
        assertEquals("add", addTool.name)

        val multiplyTool = registry.getTool("multiply")
        assertNotNull(multiplyTool)
        assertEquals("multiply", multiplyTool.name)
    }

    @Test
    fun testBuilderToolsCanBeExecuted() = runTest {
        // Test that tools added via Builder can be executed
        val calculatorTools = CalculatorTools()
        val tools = calculatorTools.asTools(ToolJson)

        val registry = ToolRegistry.builder()
            .tools(tools)
            .build()

        @Suppress("UNCHECKED_CAST")
        val addTool = registry.getTool("add") as ai.koog.agents.core.tools.reflect.ToolFromCallable
        val args = buildJsonObject {
            put("a", JsonPrimitive(5))
            put("b", JsonPrimitive(3))
        }

        val result = addTool.execute(addTool.decodeArgs(args))
        assertEquals("8", addTool.encodeResultToStringUnsafe(result))
    }

    @Test
    fun testBuilderEmptyRegistry() {
        // Test that Builder can create an empty registry
        val registry = ToolRegistry.builder().build()

        assertNotNull(registry)
        assertEquals(0, registry.tools.size)
    }

    @Test
    fun testBuilderPreventsDuplicateToolNames() = runTest {
        // Test that Builder prevents adding tools with duplicate names
        val calculatorTools = CalculatorTools()
        val tools = calculatorTools.asTools(ToolJson)
        val addTool = tools.first { it.descriptor.name == "add" }

        try {
            ToolRegistry.builder()
                .tool(addTool)
                .tool(addTool) // Try to add the same tool again
                .build()

            // If we reach here, the duplicate was not prevented
            throw AssertionError("Expected exception for duplicate tool name")
        } catch (e: IllegalArgumentException) {
            // Expected: duplicate tool name should throw an exception
            assertTrue(e.message?.contains("already defined") == true)
        }
    }

    @Test
    fun testBuilderIndependence() = runTest {
        // Test that multiple builders are independent
        val calculatorTools = CalculatorTools()
        val stringTools = StringTools()

        val calcTools = calculatorTools.asTools(ToolJson)
        val strTools = stringTools.asTools(ToolJson)

        val builder1 = ToolRegistry.builder()
        val builder2 = ToolRegistry.builder()

        builder1.tools(calcTools)
        builder2.tools(strTools)

        val registry1 = builder1.build()
        val registry2 = builder2.build()

        assertEquals(2, registry1.tools.size)
        assertEquals(1, registry2.tools.size)

        assertTrue(registry1.tools.any { it.name == "add" })
        assertTrue(registry2.tools.any { it.name == "concat" })
        assertTrue(registry1.tools.none { it.name == "concat" })
        assertTrue(registry2.tools.none { it.name == "add" })
    }

    @Test
    fun testToolRegistryBuilderClass() {
        // Test that ToolRegistryBuilder is accessible
        val builder = ToolRegistry.builder()

        // Verify that ToolRegistryBuilder has the expected methods
        assertNotNull(builder)

        // Test that it can be used fluently
        val registry = builder.build()
        assertNotNull(registry)
    }

    @Test
    fun testBuilderWithMixedToolSources() = runTest {
        // Test adding tools from different sources
        val calculatorTools = CalculatorTools()
        val stringTools = StringTools()

        val calcTools = calculatorTools.asTools(ToolJson)
        val strTools = stringTools.asTools(ToolJson)

        val registry = ToolRegistry.builder()
            .tool(calcTools[0]) // Add individual tool
            .tools(listOf(calcTools[1])) // Add as list
            .tools(strTools) // Add another list
            .build()

        assertEquals(3, registry.tools.size)
        assertTrue(registry.tools.any { it.name == "add" })
        assertTrue(registry.tools.any { it.name == "multiply" })
        assertTrue(registry.tools.any { it.name == "concat" })
    }
}
