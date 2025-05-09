package ai.grazie.code.agents.core.tools.serialization

import ai.grazie.code.agents.core.tools.tools.CollectToolsForStageTool
import ai.grazie.code.agents.core.tools.tools.StageTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ToolStageTest {
    val stage = "stage"
    val toolListName = "${stage}_tools"
    private val tool1 = SampleTool("tool_1")
    private val tool2 = SampleTool("tool_2")

    @Test
    fun testBuilderBuildsValidStage() {
        val descriptors = StageTool(stage, toolListName) {
            tool(tool1)
            tool(tool2)
        }.tools.map { it.descriptor }

        assertEquals(
            expected = listOf(
                tool1,
                tool2,
                CollectToolsForStageTool(toolListName, listOf(tool1, tool2).map { it.descriptor })
            ).map { it.descriptor },
            actual = descriptors,
        )
    }

    @Test
    fun testBuilderFailsOnEmpty() {
        assertFailsWith<IllegalArgumentException> {
            StageTool(stage, toolListName) {}
        }
    }

    @Test
    fun testBuilderFailsOnDuplicatedTools() {
        assertFailsWith<IllegalArgumentException> {
            StageTool(stage, toolListName) {
                tool(tool1)
                tool(tool1)
            }
        }
    }
}