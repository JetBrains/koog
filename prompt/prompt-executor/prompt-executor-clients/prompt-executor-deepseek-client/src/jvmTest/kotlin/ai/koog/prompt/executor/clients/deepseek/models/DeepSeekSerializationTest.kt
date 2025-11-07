package ai.koog.prompt.executor.clients.deepseek.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamOptions
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolFunction
import ai.koog.test.utils.runWithBothJsonConfigurations
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class DeepSeekSerializationTest {

    @Test
    fun `test basic serialization without optional fields`() =
        runWithBothJsonConfigurations("basic serialization without optional fields") { json ->
            val request = DeepSeekChatCompletionRequest(
                model = "deepseek-chat",
                messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
                temperature = 0.7,
                maxTokens = 1000,
                stream = false
            )

            val jsonString = json.encodeToString(DeepSeekChatCompletionRequest.serializer(), request)

            jsonString shouldEqualJson
                // language=json
                """
            {
                "model": "deepseek-chat",
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
    fun `test serialization with DeepSeek-specific fields`() =
        runWithBothJsonConfigurations("test serialization with DeepSeek-specific fields") { json ->
            val request = DeepSeekChatCompletionRequest(
                model = "deepseek-chat",
                messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
                temperature = 0.8,
                frequencyPenalty = 0.5,
                presencePenalty = 0.3,
                logprobs = true,
                topLogprobs = 5,
                topP = 0.9,
                stop = listOf("END", "STOP")
            )

            val jsonString = json.encodeToString(DeepSeekChatCompletionRequest.serializer(), request)

            jsonString shouldEqualJson
                // language=json
                """
            {
                "model": "deepseek-chat",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.8,
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
    fun `test deserialization serialization with DeepSeek-specific fields`() =
        runWithBothJsonConfigurations("test deserialization with DeepSeek-specific fields") { json ->
            val jsonInput =
                // language=json
                """
            {
                "model": "deepseek-reasoner",
                "messages": [
                    {
                        "role": "user",
                        "content": "Test message"
                    }
                ],
                "temperature": 0.5,
                "frequencyPenalty": 0.2,
                "presencePenalty": 0.1,
                "logprobs": true,
                "topLogprobs": 3,
                "topP": 0.95,
                "stop": ["STOP", "END"],
                "stream": true,
                "maxTokens": 2048
            }
                """.trimIndent()

            val request = json.decodeFromString(DeepSeekChatCompletionRequest.serializer(), jsonInput)
            val serialized = json.encodeToString(DeepSeekChatCompletionRequest.serializer(), request)
            serialized shouldEqualJson jsonInput
        }

    @Test
    fun `test serialization with additionalProperties`() =
        runWithBothJsonConfigurations("serialization with additionalProperties") { json ->
            val request = DeepSeekChatCompletionRequest(
                model = "deepseek-chat",
                messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
                temperature = 0.7,
                additionalProperties = mapOf(
                    "customString" to JsonPrimitive("value"),
                    "customNumber" to JsonPrimitive(100),
                    "customBoolean" to JsonPrimitive(true)
                )
            )

            val jsonString = json.encodeToString(DeepSeekChatCompletionRequestSerializer, request)

            jsonString shouldEqualJson
                // language=json
                """
            {
                "model": "deepseek-chat",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.7,
                "customString": "value",
                "customNumber": 100,
                "customBoolean": true
            }
                """.trimIndent()
        }

    @Test
    fun `test deserialization with additionalProperties`() =
        runWithBothJsonConfigurations("deserialization with additionalProperties") { json ->
            val jsonInput =
                """
            {
                "model": "deepseek-chat",
                "messages": [ { "role": "user", "content": "Hello" } ],
                "temperature": 0.7,
                "customString": "value",
                "customNumber": 100,
                "customBoolean": true
            }
                """.trimIndent()

            val request = json.decodeFromString(DeepSeekChatCompletionRequestSerializer, jsonInput)
            val props = request.additionalProperties
            withClue("additionalProperties should be in a deserialized JSON") {
                props shouldNotBe null
            }
            props?.get("customString").toString() shouldBe "\"value\""
            props?.get("customNumber").toString() shouldBe "100"
            props?.get("customBoolean").toString() shouldBe "true"
        }

    @Test
    fun `test serialization of extended parameters`() =
        runWithBothJsonConfigurations("test serialization of extended parameters") { json ->
            val tool = OpenAITool(
                OpenAIToolFunction(
                    name = "weather",
                    description = "Get weather",
                    parameters = JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("object"),
                            "properties" to JsonObject(emptyMap())
                        )
                    ),
                    strict = true
                )
            )

            val request = DeepSeekChatCompletionRequest(
                model = "deepseek-chat",
                messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
                temperature = 0.4,
                maxTokens = 1024,
                stream = true,
                tools = listOf(tool),
                toolChoice = OpenAIToolChoice.function("weather"),
                responseFormat = OpenAIResponseFormat.JsonObject(),
                streamOptions = OpenAIStreamOptions(includeUsage = true),
                logprobs = true,
                topLogprobs = 10,
                topP = 0.8,
                frequencyPenalty = 0.1,
                presencePenalty = 0.2,
                stop = listOf("END")
            )

            val jsonString = json.encodeToString(DeepSeekChatCompletionRequest.serializer(), request)

            jsonString shouldEqualJson
                // language=json
                """
            {
                "model": "deepseek-chat",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.4,
                "maxTokens": 1024,
                "stream": true,
                "tools": [
                    {
                        "type": "function",
                        "function": {
                            "name": "weather",
                            "description": "Get weather",
                            "parameters": {
                                "type": "object",
                                "properties": {}
                            },
                            "strict": true
                        }
                    }
                ],
                "toolChoice": {
                    "type": "function",
                    "function": {
                        "name": "weather"
                    }
                },
                "responseFormat": {
                    "type": "json_object"
                },
                "streamOptions": {
                    "includeUsage": true
                },
                "logprobs": true,
                "topLogprobs": 10,
                "topP": 0.8,
                "frequencyPenalty": 0.1,
                "presencePenalty": 0.2,
                "stop": ["END"]
            }
                """.trimIndent()
        }

    @Test
    fun `test deserialization of extended parameters`() =
        runWithBothJsonConfigurations("deserialization of extended parameters") { json ->
            val jsonInput =
                // language=json
                """
            {
                "model": "deepseek-chat",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.4,
                "maxTokens": 1024,
                "stream": true,
                "tools": [
                    {
                        "type": "function",
                        "function": {
                            "name": "weather",
                            "description": "Get weather",
                            "parameters": {
                                "type": "object",
                                "properties": {}
                            },
                            "strict": true
                        }
                    }
                ],
                "toolChoice": {
                    "type": "function",
                    "function": {
                        "name": "weather"
                    }
                },
                "responseFormat": {
                    "type": "json_object"
                },
                "streamOptions": {
                    "includeUsage": true
                },
                "logprobs": true,
                "topLogprobs": 10,
                "topP": 0.8,
                "frequencyPenalty": 0.1,
                "presencePenalty": 0.2,
                "stop": ["END"]
            }
                """.trimIndent()

            val request = json.decodeFromString(DeepSeekChatCompletionRequest.serializer(), jsonInput)

            request.model shouldBe "deepseek-chat"
            request.temperature shouldBe 0.4
            request.maxTokens shouldBe 1024
            request.stream shouldBe true
            request.logprobs shouldBe true
            request.topLogprobs shouldBe 10
            request.topP shouldBe 0.8
            request.frequencyPenalty shouldBe 0.1
            request.presencePenalty shouldBe 0.2
            request.stop shouldBe listOf("END")

            // Verify tools
            request.tools?.size shouldBe 1
            val tool = request.tools!![0]
            tool.function.name shouldBe "weather"
            tool.function.description shouldBe "Get weather"
            tool.function.strict shouldBe true

            // Verify toolChoice
            request.toolChoice?.let { toolChoice ->
                when (toolChoice) {
                    is OpenAIToolChoice.Function -> {
                        toolChoice.function.name shouldBe "weather"
                    }

                    else -> kotlin.test.fail("Expected OpenAIToolChoice.Function")
                }
            }

            // Verify responseFormat
            request.responseFormat?.let { format ->
                when (format) {
                    is OpenAIResponseFormat.JsonObject -> {
                        // Expected type
                    }

                    else -> kotlin.test.fail("Expected OpenAIResponseFormat.JsonObject")
                }
            }

            // Verify streamOptions
            request.streamOptions?.includeUsage shouldBe true
        }
}
