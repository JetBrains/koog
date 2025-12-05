package ai.koog.agents.core.agent.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentExecutionInfoTest {

    @Test
    fun testRootExecutionInfo() {
        val root = AgentExecutionInfo(parent = null, partName = "root")

        assertNull(root.parent)
        assertEquals("root", root.partName)
        assertEquals("root", root.path())
    }

    @Test
    fun testNestedExecutionInfo() {
        val root = AgentExecutionInfo(parent = null, partName = "root")
        val child = AgentExecutionInfo(parent = root, partName = "child")

        assertEquals(root, child.parent)
        assertEquals("child", child.partName)
        assertEquals("root/child", child.path())
    }

    @Test
    fun testPathWithDeepHierarchy() {
        val root = AgentExecutionInfo(parent = null, partName = "root")
        val level1 = AgentExecutionInfo(parent = root, partName = "level1")
        val level2 = AgentExecutionInfo(parent = level1, partName = "level2")
        val level3 = AgentExecutionInfo(parent = level2, partName = "level3")

        assertEquals("root/level1/level2/level3", level3.path())
    }

    @Test
    fun testPathWithCustomSeparator() {
        val root = AgentExecutionInfo(parent = null, partName = "root")
        val child = AgentExecutionInfo(parent = root, partName = "child")
        val grandchild = AgentExecutionInfo(parent = child, partName = "grandchild")

        assertEquals("root.child.grandchild", grandchild.path("."))
        assertEquals("root child grandchild", grandchild.path(" "))
        assertEquals("root -> child -> grandchild", grandchild.path(" -> "))
    }

    @Test
    fun testDefaultPathSeparator() {
        assertEquals("/", AgentExecutionInfo.defaultPathSeparator)
    }

    @Test
    fun testPathWithDefaultSeparator() {
        val root = AgentExecutionInfo(parent = null, partName = "root")
        val child = AgentExecutionInfo(parent = root, partName = "child")

        assertEquals("root/child", child.path(null))
        assertEquals("root/child", child.path())
    }

    @Test
    fun testEmptyPartName() {
        val root = AgentExecutionInfo(parent = null, partName = "")
        val child = AgentExecutionInfo(parent = root, partName = "child")

        assertEquals("/child", child.path())
    }

    @Test
    fun testMultipleEmptyPartName() {
        val root = AgentExecutionInfo(parent = null, partName = "")
        val child = AgentExecutionInfo(parent = root, partName = "")

        assertEquals("/", child.path())
    }
}
