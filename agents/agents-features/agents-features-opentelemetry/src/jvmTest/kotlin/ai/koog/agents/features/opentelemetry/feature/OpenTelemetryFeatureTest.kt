package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenTelemetryFeatureTest {

    //region AppendRunId

    @Test
    fun testAppendRunIdForTopLevelPathWithoutRunId() {
        val executionInfo = AgentExecutionInfo(null, "parent")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendRunId(executionInfo, null)
        assertEquals("parent", patchedInfo.path())
    }

    @Test
    fun testAppendRunIdForTopLevelPathWithRunId() {
        val executionInfo = AgentExecutionInfo(null, "parent")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendRunId(executionInfo, "test-run-id")
        assertEquals("parent/test-run-id", patchedInfo.path())
    }

    @Test
    fun testAppendRunIdForNonTopLevelPath() {
        val executionInfo = AgentExecutionInfo(AgentExecutionInfo(null, "parent"), "child")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendRunId(executionInfo, "test-run-id")
        assertEquals("parent/test-run-id/child", patchedInfo.path())
    }

    @Test
    fun testAppendRunIdForMultipleChildren() {
        val executionInfo = AgentExecutionInfo(AgentExecutionInfo(AgentExecutionInfo(null, "parent"), "child1"), "child2")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendRunId(executionInfo, "test-run-id")
        assertEquals("parent/test-run-id/child1/child2", patchedInfo.path())
    }

    //endregion AppendRunId

    //region AppendId

    @Test
    fun testAppendIdForTopLevelExecutionInfo() {
        val executionInfo = AgentExecutionInfo(null, "parent")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendId(executionInfo, "test-id")
        assertEquals("parent/test-id", patchedInfo.path())
    }

    @Test
    fun testAppendIdForPath() {
        val executionInfo = AgentExecutionInfo(AgentExecutionInfo(null, "parent"), "child")
        val openTelemetry = OpenTelemetry.Feature
        val patchedInfo = openTelemetry.appendId(executionInfo, "test-id")
        assertEquals("parent/child/test-id", patchedInfo.path())
    }

    //endregion AppendId
}
