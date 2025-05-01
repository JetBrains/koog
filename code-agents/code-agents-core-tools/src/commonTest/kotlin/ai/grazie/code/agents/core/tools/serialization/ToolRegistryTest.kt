package ai.grazie.code.agents.core.tools.serialization

import ai.grazie.code.agents.core.tools.SimpleToolRegistry
import ai.grazie.code.agents.core.tools.StageToolListTool
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.ToolStage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolRegistryTest {
    private val stage1 = "stage_1"
    private val stage1ToolListName = "${stage1}_tools"
    private val tool1 = SampleTool("tool_1")
    private val tool2 = SampleTool("tool_2")

    private val stage2 = "stage_2"
    private val stage2ToolListName = "${stage2}_tools"
    private val tool3 = SampleTool("tool_3")

    private val stage3 = "stage_3"
    private val stage3ToolListName = "${stage3}_tools"
    private val tool4 = SampleTool("tool_4")

    private val sampleRegistry = ToolRegistry {
        stage(stage1, stage1ToolListName) {
            tool(tool1)
            tool(tool2)
        }

        stage(stage2, stage2ToolListName){
            tool(tool3)
        }
    }

    private val additionalRegistry = ToolRegistry {
        stage(stage3, stage3ToolListName) {
            tool(tool4)
        }
    }

    private val additionalRegistry2 = ToolRegistry {
        stage(stage2, stage2ToolListName) {
            tool(tool4)
        }
    }

    private val registryWithSameTools = ToolRegistry {
        stage(stage3, stage3ToolListName) {
            tool(tool1)
            tool(tool2)
        }
    }

    @Test
    fun testBuilderBuildsValidRegistry() {
        assertEquals(
            expected = mapOf(
                stage1 to listOf(
                    tool1,
                    tool2,
                    StageToolListTool(stage1ToolListName, listOf(tool1, tool2).map { it.descriptor })
                ).map { it.descriptor },

                stage2 to listOf(
                    tool3,
                    StageToolListTool(stage2ToolListName, listOf(tool3.descriptor))
                ).map { it.descriptor}
            ),
            actual = sampleRegistry.stagesToolDescriptors,
            message = "Registry descriptor does not match expected"
        )
    }

    @Test
    fun testGetStageByTool() {
        assertEquals(stage2, sampleRegistry.getStageByTool(tool3.name).name)
        assertNull(sampleRegistry.getStageByToolOrNull("unknown_tool"), "Unknown tool should return null")
    }

    @Test
    fun testGetTool() {
        assertEquals<Tool<*, *>>(tool3, sampleRegistry.getStageByName(stage2).getTool(tool3.name))

        assertFailsWith<IllegalArgumentException>("Should fail on unknown tool") {
            sampleRegistry.getStageByName(stage1).getTool(tool3.name)
        }
    }

    @Test
    fun testCombineRegistriesWithSameToolsAcrossStages() {
        val combinedRegistry = sampleRegistry + registryWithSameTools
        assertEquals(stage1, combinedRegistry.getStageByTool(tool1.name).name)
        assertEquals(stage1, combinedRegistry.getStageByTool(tool2.name).name)
        assertEquals(stage2, combinedRegistry.getStageByTool(tool3.name).name)
    }

    @Test
    fun testCombineRegistriesWithDifferentStages() {
        val combinedRegistry = sampleRegistry + additionalRegistry

        assertEquals(stage1, combinedRegistry.getStageByTool(tool1.name).name)
        assertEquals(stage2, combinedRegistry.getStageByTool(tool3.name).name)
        assertEquals(stage3, combinedRegistry.getStageByTool(tool4.name).name)
    }

    @Test
    fun testCombineRegistriesWithSameStages() {
        val combinedRegistry = sampleRegistry + additionalRegistry2

        assertEquals(2, combinedRegistry.stagesToolDescriptors.keys.size)
        assertEquals(3, combinedRegistry.stagesToolDescriptors[stage2]!!.size)
        assertEquals(3, combinedRegistry.stagesToolDescriptors[stage1]!!.size)
        assertEquals(stage1, combinedRegistry.getStageByTool(tool1.name).name)
        assertEquals(stage2, combinedRegistry.getStageByTool(tool4.name).name)
        assertEquals(stage2, combinedRegistry.getStageByTool(tool3.name).name)
    }

    @Test
    fun testCombineWithEmptyRegistry() {
        val combinedRegistry = sampleRegistry + ToolRegistry.EMPTY

        assertEquals(2, combinedRegistry.stagesToolDescriptors.keys.size)
        assertEquals(3, combinedRegistry.stagesToolDescriptors[stage1]!!.size)
        assertEquals(2, combinedRegistry.stagesToolDescriptors[stage2]!!.size)
    }

    @Test
    fun testSimpleToolRegistryCreatesDefaultStage() {
        val simpleTool = SampleTool("simple_tool")
        val simpleRegistry = SimpleToolRegistry {
            tool(simpleTool)
        }

        // Verify that the registry has a single stage with the default name
        assertEquals(1, simpleRegistry.stagesToolDescriptors.keys.size)
        assertEquals(true, simpleRegistry.stagesToolDescriptors.containsKey(ToolStage.DEFAULT_STAGE_NAME))

        // Verify that the stage can be retrieved by name
        val stage = simpleRegistry.getStageByName(ToolStage.DEFAULT_STAGE_NAME)
        assertEquals(ToolStage.DEFAULT_STAGE_NAME, stage.name)
    }

    @Test
    fun testSimpleToolRegistryCreatesFromStage() {
        val simpleTool = SampleTool("simple_tool")

        fun toolStage(): List<SampleTool> = listOf(simpleTool)

        // Test with function that returns a list of tools
        val simpleRegistry = SimpleToolRegistry {
            tools(toolStage())
        }

        // Verify that the registry has a single stage with the default name
        assertEquals(1, simpleRegistry.stagesToolDescriptors.keys.size)
        assertEquals(true, simpleRegistry.stagesToolDescriptors.containsKey(ToolStage.DEFAULT_STAGE_NAME))

        // Verify that the stage can be retrieved by name
        val stage1 = simpleRegistry.getStageByName(ToolStage.DEFAULT_STAGE_NAME)
        assertEquals(ToolStage.DEFAULT_STAGE_NAME, stage1.name)

        // Test with direct list of tools
        val simpleRegistry2 = SimpleToolRegistry() {
            tools(toolStage())
        }

        // Verify that the registry has a single stage with the default name
        assertEquals(1, simpleRegistry2.stagesToolDescriptors.keys.size)
        assertEquals(true, simpleRegistry2.stagesToolDescriptors.containsKey(ToolStage.DEFAULT_STAGE_NAME))

        // Verify that the stage can be retrieved by name
        val stage2 = simpleRegistry2.getStageByName(ToolStage.DEFAULT_STAGE_NAME)
        assertEquals(ToolStage.DEFAULT_STAGE_NAME, stage2.name)
    }

    @Test
    fun testSimpleToolRegistryRegistersTools() {
        val simpleTool1 = SampleTool("simple_tool_1")
        val simpleTool2 = SampleTool("simple_tool_2")
        val simpleRegistry = SimpleToolRegistry {
            tool(simpleTool1)
            tool(simpleTool2)
        }

        // Verify that the tools are registered in the default stage
        val stage = simpleRegistry.getStageByName(ToolStage.DEFAULT_STAGE_NAME)
        assertEquals<Tool<*, *>>(simpleTool1, stage.getTool(simpleTool1.name))
        assertEquals<Tool<*, *>>(simpleTool2, stage.getTool(simpleTool2.name))

        // Verify that the stage contains the expected number of tools (including the tool list tool)
        assertEquals(3, simpleRegistry.stagesToolDescriptors[ToolStage.DEFAULT_STAGE_NAME]!!.size)

        // Verify that the tools can be found by name
        assertEquals(ToolStage.DEFAULT_STAGE_NAME, simpleRegistry.getStageByTool(simpleTool1.name).name)
        assertEquals(ToolStage.DEFAULT_STAGE_NAME, simpleRegistry.getStageByTool(simpleTool2.name).name)
    }

    @Test
    fun testCombineSimpleToolRegistryWithOtherRegistry() {
        val simpleTool = SampleTool("simple_tool")
        val simpleRegistry = SimpleToolRegistry {
            tool(simpleTool)
        }

        // Combine with a regular registry
        val combinedRegistry = simpleRegistry + sampleRegistry

        // Verify that the combined registry has all stages
        assertEquals(3, combinedRegistry.stagesToolDescriptors.keys.size)
        assertEquals(true, combinedRegistry.stagesToolDescriptors.containsKey(ToolStage.DEFAULT_STAGE_NAME))
        assertEquals(true, combinedRegistry.stagesToolDescriptors.containsKey(stage1))
        assertEquals(true, combinedRegistry.stagesToolDescriptors.containsKey(stage2))

        // Verify that tools from both registries are accessible
        assertEquals(ToolStage.DEFAULT_STAGE_NAME, combinedRegistry.getStageByTool(simpleTool.name).name)
        assertEquals(stage1, combinedRegistry.getStageByTool(tool1.name).name)
        assertEquals(stage2, combinedRegistry.getStageByTool(tool3.name).name)
    }

    @Test
    fun testGetToolByArgs() = runTest {
        val tool = sampleRegistry.getTool(tool1.name) as SampleTool
        assertTrue(tool in listOf(tool1, tool2, tool3))

        // Test with unknown args type
        assertFailsWith<IllegalArgumentException>("Should fail on unknown args type") {
            sampleRegistry.getTool("unknown_tool")
        }
    }
}
