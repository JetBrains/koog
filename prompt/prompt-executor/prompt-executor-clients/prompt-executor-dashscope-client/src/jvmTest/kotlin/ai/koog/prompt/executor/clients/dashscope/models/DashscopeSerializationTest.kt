package ai.koog.prompt.executor.clients.dashscope.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class DashscopeSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    @Test
    fun `test basic serialization without optional fields`() {
        val request = DashscopeChatCompletionRequest(
            model = "qwen-plus",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.7,
            maxTokens = 1000,
            stream = false
        )

        val jsonString = json.encodeToString(DashscopeChatCompletionRequest.serializer(), request)

        jsonString shouldEqualJson
            // language=json
            """
            {
                "model": "qwen-plus",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.7,
                "maxTokens": 1000,
                "stream": false
            }
            """.trimIndent()
    }

    @Test
    fun `test serialization with DashScope-specific fields`() {
        val request = DashscopeChatCompletionRequest(
            model = "qwen-plus",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.8,
            enableSearch = true,
            parallelToolCalls = false,
            enableThinking = true,
            frequencyPenalty = 0.5,
            presencePenalty = 0.3,
            logprobs = true,
            topLogprobs = 5,
            topP = 0.9,
            stop = listOf("END", "STOP")
        )

        val jsonString = json.encodeToString(DashscopeChatCompletionRequest.serializer(), request)

        jsonString shouldEqualJson
            // language=json
            """
            {
                "model": "qwen-plus",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.8,
                "enableSearch": true,
                "parallelToolCalls": false,
                "enableThinking": true,
                "frequencyPenalty": 0.5,
                "presencePenalty": 0.3,
                "logprobs": true,
                "topLogprobs": 5,
                "topP": 0.9,
                "stop": ["END", "STOP"]
            }
            """.trimIndent()
    }

    @Test
    fun `test deserialization without DashScope-specific fields`() {
        val jsonInput =
            // language=json
            """
            {
                "model": "qwen-max",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.7,
                "maxTokens": 1000,
                "stream": false
            }
            """.trimIndent()

        val request = json.decodeFromString(DashscopeChatCompletionRequest.serializer(), jsonInput)

        val serialized = json.encodeToString(DashscopeChatCompletionRequest.serializer(), request)

        serialized shouldEqualJson jsonInput
    }

    @Test
    fun `test deserialization with DashScope-specific fields`() {
        val jsonInput =
            // language=json
            """
            {
                "model": "qwen-long",
                "messages": [
                    {
                        "role": "user",
                        "content": "Test message"
                    }
                ],
                "temperature": 0.5,
                "enableSearch": true,
                "parallelToolCalls": false,
                "enableThinking": true,
                "frequencyPenalty": 0.2,
                "presencePenalty": 0.1,
                "logprobs": true,
                "topLogprobs": 3,
                "topP": 0.95,
                "stop": ["STOP", "END"]
            }
            """.trimIndent()

        val request = json.decodeFromString(DashscopeChatCompletionRequest.serializer(), jsonInput)

        val serialized = json.encodeToString(DashscopeChatCompletionRequest.serializer(), request)

        serialized shouldEqualJson jsonInput
    }

    @Test
    fun `test chat completion response deserialization with systemFingerprint`() {
        val jsonInput =
            // language=json
            """
            {
                "id": "chatcmpl-123",
                "object": "chat.completion",
                "created": 1677652288,
                "model": "qwen-plus",
                "choices": [
                    {
                        "index": 0,
                        "finishReason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": "Hello! How can I help you?"
                        }
                    }
                ],
                "usage": {
                    "promptTokens": 10,
                    "completionTokens": 20,
                    "totalTokens": 30
                }
            }
            """.trimIndent()

        val response = json.decodeFromString(DashscopeChatCompletionResponse.serializer(), jsonInput)

        response.systemFingerprint shouldBe null
    }

    @Test
    fun `test chat completion response deserialization without systemFingerprint`() {
        val jsonInput =
            // language=json
            """
            {
                "id": "chatcmpl-456",
                "object": "chat.completion",
                "created": 1677652300,
                "model": "qwen-max",
                "choices": [
                    {
                        "index": 0,
                        "finishReason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": "Test response"
                        }
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString(DashscopeChatCompletionResponse.serializer(), jsonInput)

        response.systemFingerprint shouldBe null
    }

    @Test
    fun `test chat completion stream response deserialization with systemFingerprint`() {
        val jsonInput =
            // language=json
            """
            {
                "id": "chatcmpl-789",
                "object": "chat.completion.chunk",
                "created": 1677652400,
                "model": "qwen-turbo",
                "choices": [
                    {
                        "index": 0,
                        "delta": {
                            "role": "assistant",
                            "content": "Hello"
                        }
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString(DashscopeChatCompletionStreamResponse.serializer(), jsonInput)

        response.systemFingerprint shouldBe null
    }

    @Test
    fun `test chat completion stream response deserialization without systemFingerprint`() {
        val jsonInput =
            // language=json
            """
            {
                "id": "chatcmpl-012",
                "object": "chat.completion.chunk",
                "created": 1677652500,
                "model": "qwen-long",
                "choices": [
                    {
                        "index": 0,
                        "finishReason": "stop",
                        "delta": {
                            "content": "Final chunk"
                        }
                    }
                ],
                "usage": {
                    "promptTokens": 15,
                    "completionTokens": 25,
                    "totalTokens": 40
                }
            }
            """.trimIndent()

        val response = json.decodeFromString(DashscopeChatCompletionStreamResponse.serializer(), jsonInput)

        response.systemFingerprint shouldBe null
    }
}
