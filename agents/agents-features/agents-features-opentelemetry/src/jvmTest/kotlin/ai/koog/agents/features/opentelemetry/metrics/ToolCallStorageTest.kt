package ai.koog.agents.features.opentelemetry.metrics

import ai.koog.agents.features.opentelemetry.metric.ToolCallStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ToolCallStorageTest {
    @Test
    fun `test tool call storage saves tool call`() {
        val toolCallStorage = ToolCallStorage()

        val expectedToolCallId = "id"
        val expectedToolCallName = "name"

        // Add tool call to storage and check that it stores properly
        val startedToolCall = toolCallStorage.addToolCall(expectedToolCallId, expectedToolCallName)
        assertNotNull(startedToolCall)
        assertEquals(startedToolCall.name, expectedToolCallName)
        assertNotNull(startedToolCall.timeStarted)
        assertNull(startedToolCall.timeEnded)

        // Finish this tool call and check that it stores properly
        val finishedToolCall = toolCallStorage.endToolCallAndReturn(expectedToolCallId)
        assertNotNull(finishedToolCall)
        assertEquals(finishedToolCall.name, expectedToolCallName)
        assertNotNull(finishedToolCall.timeStarted)
        assertNotNull(finishedToolCall.timeEnded)
    }

    @Test
    fun `test tool calls storage does not save tool call twice`() {
        val toolCallStorage = ToolCallStorage()

        val expectedToolCallId = "id"
        val expectedToolCallName = "name"

        val firstSaveToolCall = toolCallStorage.addToolCall(expectedToolCallId, expectedToolCallName)
        val secondSaveToolCall = toolCallStorage.addToolCall(expectedToolCallId, expectedToolCallName)
        assertNotNull(firstSaveToolCall)
        assertNull(secondSaveToolCall)
    }

    @Test
    fun `test tool calls storage does not end tool call twice`() {
        val toolCallStorage = ToolCallStorage()

        val expectedToolCallId = "id"
        val expectedToolCallName = "name"

        toolCallStorage.addToolCall(expectedToolCallId, expectedToolCallName)

        val firstEndToolCall = toolCallStorage.endToolCallAndReturn(expectedToolCallId)
        val secondEndToolCall = toolCallStorage.endToolCallAndReturn(expectedToolCallId)
        assertNotNull(firstEndToolCall)
        assertNull(secondEndToolCall)
    }
}
