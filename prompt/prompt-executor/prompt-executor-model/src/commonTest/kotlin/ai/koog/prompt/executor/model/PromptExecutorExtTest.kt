package ai.koog.prompt.executor.model

import ai.koog.prompt.executor.model.PromptExecutorExt.firstResponse
import ai.koog.prompt.executor.model.PromptExecutorExt.firstResponseOrNull
import ai.koog.prompt.executor.model.PromptExecutorExt.singleResponse
import ai.koog.prompt.executor.model.PromptExecutorExt.singleResponseOrNull
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PromptExecutorExtTest {

    private val testMetainfo = ResponseMetaInfo(timestamp = Instant.parse("2023-01-01T00:00:00Z"))

    //region Single / SingleOrNull

    @Test
    fun testSingleResponseOneResponse() {
        val response = Message.Assistant("Test response", testMetainfo)
        val responses = listOf(response)

        val actualResponse = responses.singleResponse()
        assertEquals(response, actualResponse)
    }

    @Test
    fun testSingleResponseEmptyList() {
        val responses = emptyList<Message.Response>()

        assertFailsWith<NoSuchElementException> {
            responses.singleResponse()
        }
    }

    @Test
    fun testSingleResponseMultipleResponses() {
        val response1 = Message.Assistant("Test response 1", testMetainfo)
        val response2 = Message.Assistant("Test response 2", testMetainfo)
        val responses = listOf(response1, response2)

        assertFailsWith<IllegalArgumentException> {
            responses.singleResponse()
        }
    }

    @Test
    fun testSingleResponseOrNullOneResponse() {
        val response = Message.Assistant("Test response", testMetainfo)
        val responses = listOf(response)
        val actualResponse = responses.singleResponseOrNull()

        assertEquals(response, actualResponse)
    }

    @Test
    fun testSingleResponseOrNullEmptyList() {
        val responses = emptyList<Message.Response>()
        val actualResponse = responses.singleResponseOrNull()

        assertNull(actualResponse)
    }

    @Test
    fun testSingleResponseOrNullMultipleResponses() {
        val response1 = Message.Assistant("Test response 1", testMetainfo)
        val response2 = Message.Assistant("Test response 2", testMetainfo)
        val responses = listOf(response1, response2)

        val actualResponse = responses.singleResponseOrNull()
        assertNull(actualResponse)
    }

    //endregion Single / SingleOrNull

    //region First / FirstOrNull

    @Test
    fun testFirstResponseOneResponse() {
        val response1 = Message.Assistant("Test response 1", testMetainfo)
        val response2 = Message.Assistant("Test response 2", testMetainfo)
        val responses = listOf(response1, response2)

        val actualResponse = responses.firstResponse()
        assertEquals(response1, actualResponse)
    }

    @Test
    fun testFirstResponseEmptyList() {
        val responses = emptyList<Message.Response>()
        assertFailsWith<NoSuchElementException> {
            responses.firstResponse()
        }
    }

    @Test
    fun testFirstResponseOrNullOneResponse() {
        val response = Message.Assistant("Test response", testMetainfo)
        val responses = listOf(response)

        val actualResponse = responses.firstResponseOrNull()
        assertEquals(response, actualResponse)
    }

    @Test
    fun testFirstResponseOrNullEmptyList() {
        val responses = emptyList<Message.Response>()
        val actualResponse = responses.firstResponseOrNull()

        assertNull(actualResponse)
    }

    @Test
    fun testFirstResponseOrNullMultipleResponses() {
        val response1 = Message.Assistant("Test response 1", testMetainfo)
        val response2 = Message.Assistant("Test response 2", testMetainfo)
        val responses = listOf(response1, response2)

        val actualResponse = responses.firstResponseOrNull()
        assertEquals(response1, actualResponse)
    }

    //endregion First / FirstOrNull
}
