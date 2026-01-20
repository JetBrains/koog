package ai.koog.agents.features.opentelemetry.metrics

import ai.koog.agents.features.opentelemetry.metric.EventCallStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EventCallStorageTest {
    @Test
    fun `test event call storage saves tool call`() {
        val eventCallStorage = EventCallStorage()

        val expectedToolCallId = "id"
        val expectedToolCallName = "name"

        // Add tool call to storage and check that it stores properly
        val startedToolCall = eventCallStorage.addEventCall(expectedToolCallId, expectedToolCallName)
        assertNotNull(startedToolCall)
        assertEquals(startedToolCall.name, expectedToolCallName)
        assertNotNull(startedToolCall.timeStarted)
        assertNull(startedToolCall.timeEnded)

        // Finish this tool call and check that it stores properly
        val finishedToolCall = eventCallStorage.endEventCallAndReturn(expectedToolCallId)
        assertNotNull(finishedToolCall)
        assertEquals(finishedToolCall.name, expectedToolCallName)
        assertNotNull(finishedToolCall.timeStarted)
        assertNotNull(finishedToolCall.timeEnded)
    }

    @Test
    fun `test event calls storage does not save tool call twice`() {
        val eventCallStorage = EventCallStorage()

        val expectedToolCallId = "id"
        val expectedToolCallName = "name"

        val firstSaveToolCall = eventCallStorage.addEventCall(expectedToolCallId, expectedToolCallName)
        val secondSaveToolCall = eventCallStorage.addEventCall(expectedToolCallId, expectedToolCallName)
        assertNotNull(firstSaveToolCall)
        assertNull(secondSaveToolCall)
    }

    @Test
    fun `test event calls storage does not end tool call twice`() {
        val eventCallStorage = EventCallStorage()

        val expectedToolCallId = "id"
        val expectedToolCallName = "name"

        eventCallStorage.addEventCall(expectedToolCallId, expectedToolCallName)

        val firstEndToolCall = eventCallStorage.endEventCallAndReturn(expectedToolCallId)
        val secondEndToolCall = eventCallStorage.endEventCallAndReturn(expectedToolCallId)
        assertNotNull(firstEndToolCall)
        assertNull(secondEndToolCall)
    }
}
