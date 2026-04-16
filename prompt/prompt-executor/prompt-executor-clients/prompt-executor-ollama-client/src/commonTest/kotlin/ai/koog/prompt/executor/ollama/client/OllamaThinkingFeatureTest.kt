package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatMessageDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Ollama client thinking feature support.
 *
 * These tests verify that:
 * - Thinking content is properly parsed from responses
 * - Thinking and regular content are handled correctly
 * - Thinking works with streaming responses
 * - Thinking is properly emitted in stream frames
 */
class OllamaThinkingFeatureTest {

    @Test
    fun `test parsing response with thinking content only`() = runTest {
        val thinkingContent = "Let me think about this problem step by step..."
        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = "",
                    thinking = thinkingContent
                ),
                done = true
            )
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine)
        )

        val responses = ollamaClient.execute(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        assertEquals(1, responses.size)
        val response = responses.first()
        assertTrue(response is Message.Assistant)
        // The thinking should still be in the content as it was provided
        assertTrue(response.content.contains(thinkingContent) || response.content.isEmpty())
    }

    @Test
    fun `test parsing response with both thinking and content`() = runTest {
        val thinkingContent = "I need to analyze the request carefully"
        val responseContent = "Based on my analysis, the answer is..."

        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = responseContent,
                    thinking = thinkingContent
                ),
                done = true
            )
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine)
        )

        val responses = ollamaClient.execute(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        assertEquals(1, responses.size)
        val response = responses.first()
        assertTrue(response is Message.Assistant)
        assertEquals(responseContent, response.content)
    }

    @Test
    fun `test parsing response without thinking content`() = runTest {
        val responseContent = "This is a simple response"

        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = responseContent,
                    thinking = null
                ),
                done = true
            )
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine)
        )

        val responses = ollamaClient.execute(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        assertEquals(1, responses.size)
        val response = responses.first()
        assertTrue(response is Message.Assistant)
        assertEquals(responseContent, response.content)
    }

    @Test
    fun `test parsing response with empty thinking content`() = runTest {
        val responseContent = "Response content"

        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = responseContent,
                    thinking = ""
                ),
                done = true
            )
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine)
        )

        val responses = ollamaClient.execute(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        assertEquals(1, responses.size)
        val response = responses.first()
        assertTrue(response is Message.Assistant)
        assertEquals(responseContent, response.content)
    }

    @Test
    fun `test streaming response with thinking content`() = runTest {
        val thinkingContent = "Thinking through the problem..."
        val responseContent = "Final answer"

        // For streaming, we need to simulate multiple chunks
        val streamingResponses = listOf(
            OllamaChatResponseDTO(
                model = "test-model",
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = "",
                    thinking = thinkingContent
                ),
                done = false
            ),
            OllamaChatResponseDTO(
                model = "test-model",
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = responseContent,
                    thinking = null
                ),
                done = false
            ),
            OllamaChatResponseDTO(
                model = "test-model",
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = "",
                    thinking = null
                ),
                done = true
            )
        )

        var responseIndex = 0
        val mockServer = MockStreamingOllamaChatServer {
            val response = streamingResponses.getOrNull(responseIndex)
                ?: OllamaChatResponseDTO(
                    model = "test-model",
                    message = null,
                    done = true
                )
            responseIndex++
            response
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine)
        )

        val streamFrames = ollamaClient.executeStreaming(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        ).toList()

        // Verify that we have stream frames
        assertTrue(streamFrames.isNotEmpty(), "Should have stream frames")

        // Check for thinking stream frames
        val thinkingFrames = streamFrames.filterIsInstance<StreamFrame.ReasoningDelta>()
        assertTrue(thinkingFrames.isNotEmpty(), "Should have at least one thinking frame")

        // Verify thinking content is present
        val allThinkingContent = thinkingFrames.joinToString("") { it.text.orEmpty() }
        assertTrue(allThinkingContent.contains(thinkingContent), "Thinking content should be in frames")
    }

    @Test
    fun `test streaming response with content only`() = runTest {
        val responseContent = "Streaming response content"

        val streamingResponses = listOf(
            OllamaChatResponseDTO(
                model = "test-model",
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = responseContent,
                    thinking = null
                ),
                done = false
            ),
            OllamaChatResponseDTO(
                model = "test-model",
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = "",
                    thinking = null
                ),
                done = true
            )
        )

        var responseIndex = 0
        val mockServer = MockStreamingOllamaChatServer {
            val response = streamingResponses.getOrNull(responseIndex)
                ?: OllamaChatResponseDTO(
                    model = "test-model",
                    message = null,
                    done = true
                )
            responseIndex++
            response
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine)
        )

        val streamFrames = ollamaClient.executeStreaming(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        ).toList()

        // Verify that we have stream frames
        assertTrue(streamFrames.isNotEmpty(), "Should have stream frames")

        // Check for append frames
        val textFrames = streamFrames.filterIsInstance<StreamFrame.TextDelta>()
        assertTrue(textFrames.isNotEmpty(), "Should have at least one text frame")

        // Verify content is present
        val allContent = textFrames.joinToString("") { it.text }
        assertTrue(allContent.contains(responseContent), "Response content should be in frames")
    }

    @Test
    fun `test streaming response with both thinking and content`() = runTest {
        val thinkingContent = "Internal reasoning process"
        val responseContent = "External response"

        val streamingResponses = listOf(
            OllamaChatResponseDTO(
                model = "test-model",
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = "",
                    thinking = thinkingContent
                ),
                done = false
            ),
            OllamaChatResponseDTO(
                model = "test-model",
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = responseContent,
                    thinking = null
                ),
                done = false
            ),
            OllamaChatResponseDTO(
                model = "test-model",
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = "",
                    thinking = null
                ),
                done = true
            )
        )

        var responseIndex = 0
        val mockServer = MockStreamingOllamaChatServer {
            val response = streamingResponses.getOrNull(responseIndex)
                ?: OllamaChatResponseDTO(
                    model = "test-model",
                    message = null,
                    done = true
                )
            responseIndex++
            response
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine)
        )

        val streamFrames = ollamaClient.executeStreaming(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        ).toList()

        // Verify we have stream frames
        assertTrue(streamFrames.isNotEmpty(), "Should have stream frames")

        // Check for both types of frames
        val thinkingFrames = streamFrames.filterIsInstance<StreamFrame.ReasoningDelta>()
        val textFrames = streamFrames.filterIsInstance<StreamFrame.TextDelta>()

        assertTrue(thinkingFrames.isNotEmpty(), "Should have thinking frames")
        assertTrue(textFrames.isNotEmpty(), "Should have text frames")

        // Verify content
        val allThinking = thinkingFrames.joinToString("") { it.text.orEmpty() }
        val allContent = textFrames.joinToString("") { it.text }

        assertTrue(allThinking.contains(thinkingContent), "Thinking content should be in thinking frames")
        assertTrue(allContent.contains(responseContent), "Response content should be in text frames")
    }

    @Test
    fun `test request includes think parameter`() = runTest {
        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = "Response"
                ),
                done = true
            )
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine)
        )

        ollamaClient.execute(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        val requestHistory = mockServer.requestHistory
        assertEquals(1, requestHistory.size)

        val request = requestHistory.first()
        // The think parameter should be set to true by default
        assertTrue(request.think, "Request should have think parameter set to true")
    }

    @Test
    fun `test token counts are properly extracted with thinking`() = runTest {
        val promptTokens = 50
        val responseTokens = 100

        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = "Response with thinking",
                    thinking = "This is the thinking content"
                ),
                done = true,
                promptEvalCount = promptTokens,
                evalCount = responseTokens
            )
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine)
        )

        val responses = ollamaClient.execute(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        assertEquals(1, responses.size)
        val response = responses.first()
        assertTrue(response is Message.Assistant)

        val metaInfo = assertNotNull(response.metaInfo)
        assertEquals(promptTokens, metaInfo.inputTokensCount)
        assertEquals(responseTokens, metaInfo.outputTokensCount)
    }
}
