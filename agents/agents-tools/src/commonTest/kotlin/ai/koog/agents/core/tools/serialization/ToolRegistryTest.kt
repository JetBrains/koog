package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ToolRegistryTest {
    private val tool1 = SampleTool("tool_1")
    private val tool2 = SampleTool("tool_2")
    private val tool3 = SampleTool("tool_3")
    private val tool4 = SampleTool("tool_4")

    private val sampleRegistry = ToolRegistry {
        tool(tool1)
        tool(tool2)
    }

    private val additionalRegistry = ToolRegistry {
        tool(tool3)
    }

    private val additionalRegistry2 = ToolRegistry {
        tool(tool4)
    }

    private val registryWithSameTools = ToolRegistry {
        tool(tool1)
        tool(tool2)
    }

    @Test
    fun testToolRegistryInitialState() = runTest {
        val toolRegistry = ToolRegistry {}
        assertEquals(0, toolRegistry.tools.size)
    }

    @Test
    fun testBuilderBuildsValidRegistry() {
        // Verify that the registry contains the expected tools
        assertEquals(2, sampleRegistry.tools.size)
        assertTrue(sampleRegistry.tools.contains(tool1))
        assertTrue(sampleRegistry.tools.contains(tool2))
    }

    @Test
    fun testGetTool() {
        // Test getting a tool by name
        assertEquals<Tool<*, *>>(tool1, sampleRegistry.getTool(tool1.name))
        assertEquals<Tool<*, *>>(tool2, sampleRegistry.getTool(tool2.name))

        // Test getting a tool that doesn't exist
        assertFailsWith<IllegalArgumentException>("Should fail on unknown tool") {
            sampleRegistry.getTool("unknown_tool")
        }
    }

    @Test
    fun testGetToolByType() {
        // Create a registry with a specific tool type
        val registry = ToolRegistry {
            tool(tool1)
        }

        // Test getting a tool by type
        assertEquals(tool1, registry.getTool<SampleTool>())

        // Test getting a tool type that doesn't exist
        assertFailsWith<IllegalArgumentException>("Should fail on unknown tool type") {
            registry.getTool<SimpleTool<*>>()
        }
    }

    @Test
    fun testCombineRegistriesWithSameTools() {
        val combinedRegistry = sampleRegistry + registryWithSameTools

        // The combined registry should have the same tools, with no duplicates
        assertEquals(2, combinedRegistry.tools.size)
        assertTrue(combinedRegistry.tools.contains(tool1))
        assertTrue(combinedRegistry.tools.contains(tool2))
    }

    @Test
    fun testCombineRegistriesWithDifferentTools() {
        val combinedRegistry = sampleRegistry + additionalRegistry

        // The combined registry should have all tools from both registries
        assertEquals(3, combinedRegistry.tools.size)
        assertTrue(combinedRegistry.tools.contains(tool1))
        assertTrue(combinedRegistry.tools.contains(tool2))
        assertTrue(combinedRegistry.tools.contains(tool3))
    }

    @Test
    fun testCombineWithEmptyRegistry() {
        val combinedRegistry = sampleRegistry + ToolRegistry.EMPTY

        // The combined registry should have the same tools as the original registry
        assertEquals(2, combinedRegistry.tools.size)
        assertTrue(combinedRegistry.tools.contains(tool1))
        assertTrue(combinedRegistry.tools.contains(tool2))
    }

    @Test
    fun testGetToolByArgs() = runTest {
        val tool = sampleRegistry.getTool(tool1.name) as SampleTool
        assertTrue(tool in listOf(tool1, tool2))

        // Test with unknown args type
        assertFailsWith<IllegalArgumentException>("Should fail on unknown tool") {
            sampleRegistry.getTool("unknown_tool")
        }
    }

    @Test
    fun testToolRegistryAddNonExistingTool() = runTest {
        val toolRegistry = ToolRegistry { }
        assertEquals(0, toolRegistry.tools.size)

        toolRegistry.add(tool1)
        assertEquals(1, toolRegistry.tools.size)
        assertEquals(tool1, toolRegistry.tools.single())
    }

    @Test
    fun testToolRegistryAddExistingTool() = runTest {
        val toolRegistry = ToolRegistry {
            tool(tool1)
        }
        assertEquals(1, toolRegistry.tools.size)

        toolRegistry.add(tool1)
        assertEquals(1, toolRegistry.tools.size)
        assertEquals(tool1, toolRegistry.tools.single())
    }

    @Test
    fun testToolRegistryAddAllNonExistingTools() = runTest {
        val toolRegistry = ToolRegistry {}
        assertEquals(0, toolRegistry.tools.size)

        toolRegistry.addAll(tool1, tool2)

        val expectedTools = listOf(tool1, tool2)
        assertEquals(expectedTools.size, toolRegistry.tools.size)
        assertContentEquals(expectedTools, toolRegistry.tools)
    }

    @Test
    fun testToolRegistryAddAllExistingTool() = runTest {
        val toolRegistry = ToolRegistry {
            tools(listOf(tool1,  tool2))
        }
        assertEquals(2, toolRegistry.tools.size)

        toolRegistry.addAll(tool1, tool2)

        val expectedTools = listOf(tool1, tool2)
        assertEquals(expectedTools.size, toolRegistry.tools.size)
        assertContentEquals(expectedTools, toolRegistry.tools)
    }

    @Test
    fun testToolRegistryAddAllPartiallyExistingTool() = runTest {
        @Test
        fun testToolRegistryAddAllExistingTool() = runTest {
            val toolRegistry = ToolRegistry {
                tools(listOf(tool1,  tool2))
            }
            assertEquals(2, toolRegistry.tools.size)

            toolRegistry.addAll(tool2, tool3)

            val expectedTools = listOf(tool1, tool2, tool3)
            assertEquals(expectedTools.size, toolRegistry.tools.size)
            assertContentEquals(expectedTools, toolRegistry.tools)
        }
    }

    @Test
    fun testToolRegistryAddAllEmptyList() = runTest {
        @Test
        fun testToolRegistryAddAllExistingTool() = runTest {
            val toolRegistry = ToolRegistry { }
            assertEquals(0, toolRegistry.tools.size)

            toolRegistry.addAll()

            assertTrue(toolRegistry.tools.isEmpty())
        }
    }
}
