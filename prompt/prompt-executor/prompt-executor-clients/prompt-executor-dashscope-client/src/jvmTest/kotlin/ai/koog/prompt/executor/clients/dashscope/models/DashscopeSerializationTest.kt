package ai.koog.prompt.executor.clients.dashscope.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamOptions
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolFunction
import ai.koog.test.utils.runWithBothJsonConfigurations
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

class DashscopeSerializationTest {
    @Test
    fun `test basic serialization without optional fields`() =
        runWithBothJsonConfigurations("basic serialization without optional fields") { json ->
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
    fun `test serialization with DashScope-specific fields`() =
        runWithBothJsonConfigurations("serialization with DashScope-specific fields") { json ->
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
    fun `test serialization without DashScope-specific fields`() =
        runWithBothJsonConfigurations("serialization without DashScope-specific fields") { json ->
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
    fun `test deserialization with DashScope-specific fields`() =
        runWithBothJsonConfigurations("test deserialization with  DashScope-specific fields") { json ->
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
    fun `test deserialization without DashScope-specific fields`() =
        runWithBothJsonConfigurations("test deserialization without DashScope-specific fields") { json ->
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
    fun `test chat completion response deserialization with systemFingerprint`() =
        runWithBothJsonConfigurations("test chat completion response deserialization with systemFingerprint") { json ->
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
    fun `test chat completion response deserialization without systemFingerprint`() =
        runWithBothJsonConfigurations("test chat completion response deserialization without systemFingerprint") { json ->
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
    fun `test chat completion stream response deserialization with systemFingerprint`() =
        runWithBothJsonConfigurations("test chat completion stream response deserialization with systemFingerprint") { json ->
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
    fun `test chat completion stream response deserialization without systemFingerprint`() =
        runWithBothJsonConfigurations("test chat completion stream response deserialization without systemFingerprint") { json ->
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

    @Test
    fun `test serialization with additionalProperties`() =
        runWithBothJsonConfigurations("serialization with additionalProperties") { json ->
            val request = DashscopeChatCompletionRequest(
                model = "qwen-plus",
                messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
                temperature = 0.7,
                additionalProperties = mapOf(
                    "customString" to JsonPrimitive("value"),
                    "customNumber" to JsonPrimitive(100),
                    "customBoolean" to JsonPrimitive(true)
                )
            )

            val element = json.encodeToJsonElement(DashscopeChatCompletionRequestSerializer, request)
                .jsonObject

            // Standard properties should be present
            element["model"]!!.toString() shouldBe "\"qwen-plus\""
            element["temperature"]!!.toString() shouldBe "0.7"

            // Additional properties should be flattened to the root level
            element["customString"]!!.toString() shouldBe "\"value\""
            element["customNumber"]!!.toString() shouldBe "100"
            element["customBoolean"]!!.toString() shouldBe "true"

            // the additionalProperties name itself should not be present in serialized JSON
            element["additionalProperties"] shouldBe null
        }

    @Test
    fun `test deserialization with additionalProperties`() =
        runWithBothJsonConfigurations("deserialization with additionalProperties") { json ->
            val jsonInput =
                """
            {
                "model": "qwen-plus",
                "messages": [ { "role": "user", "content": "Hello" } ],
                "temperature": 0.7,
                "customString": "value",
                "customNumber": 100,
                "customBoolean": true
            }
                """.trimIndent()

            val request = json.decodeFromString(DashscopeChatCompletionRequestSerializer, jsonInput)
            val props = request.additionalProperties
            kotlin.test.assertNotNull(props)
            props["customString"].toString() shouldBe "\"value\""
            props["customNumber"].toString() shouldBe "100"
            props["customBoolean"].toString() shouldBe "true"
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

            val request = DashscopeChatCompletionRequest(
                model = "qwen-plus",
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
                stop = listOf("END"),
                enableSearch = true,
                parallelToolCalls = true,
                enableThinking = false,
            )

            val obj = json.encodeToJsonElement(DashscopeChatCompletionRequest.serializer(), request).jsonObject

            obj["model"]!!.toString() shouldBe "\"qwen-plus\""
            obj["temperature"]!!.toString() shouldBe "0.4"
            obj["maxTokens"]!!.toString() shouldBe "1024"
            obj["stream"].toString() shouldBe "true"
            obj["topLogprobs"].toString() shouldBe "10"
            obj["topP"].toString() shouldBe "0.8"
            obj["frequencyPenalty"].toString() shouldBe "0.1"
            obj["presencePenalty"].toString() shouldBe "0.2"
            (obj["stop"] as JsonArray).size shouldBe 1
            obj["enableSearch"].toString() shouldBe "true"
            obj["parallelToolCalls"].toString() shouldBe "true"
            obj["enableThinking"].toString() shouldBe "false"

            val toolsArr = obj["tools"] as JsonArray
            toolsArr.size shouldBe 1
            val t0 = toolsArr[0].jsonObject
            val fn = t0["function"]!!.jsonObject
            fn["name"]!!.toString() shouldBe "\"weather\""
            fn["description"]!!.toString() shouldBe "\"Get weather\""

            fn["parameters"]!!.jsonObject["type"]!!.toString() shouldBe "\"object\""
            fn["strict"]!!.toString() shouldBe "true"

            val tc = obj["toolChoice"]!!.jsonObject
            tc["function"]!!.jsonObject["name"]!!.toString() shouldBe "\"weather\""

            val rf = obj["responseFormat"]!!.jsonObject
            rf["type"]!!.toString() shouldBe "\"json_object\""

            val so = obj["streamOptions"]!!.jsonObject
            so["includeUsage"].toString() shouldBe "true"
        }

    @Test
    fun `test deserialization of extended parameters`() =
        runWithBothJsonConfigurations("deserialization of extended parameters") { json ->
            val jsonInput =
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
                "stop": ["END"],
                "enableSearch": true,
                "parallelToolCalls": true,
                "enableThinking": false
            }
                """.trimIndent()

            val request = json.decodeFromString(DashscopeChatCompletionRequest.serializer(), jsonInput)

            request.model shouldBe "qwen-plus"
            request.temperature shouldBe 0.4
            request.maxTokens shouldBe 1024
            request.stream shouldBe true
            request.logprobs shouldBe true
            request.topLogprobs shouldBe 10
            request.topP shouldBe 0.8
            request.frequencyPenalty shouldBe 0.1
            request.presencePenalty shouldBe 0.2
            request.stop shouldBe listOf("END")
            request.enableSearch shouldBe true
            request.parallelToolCalls shouldBe true
            request.enableThinking shouldBe false

            // Verify tools
            request.tools?.size shouldBe 1
            val tool = request.tools!![0]
            tool.function.name shouldBe "weather"
            tool.function.description shouldBe "Get weather"
            tool.function.strict shouldBe true

            // Verify streamOptions
            request.streamOptions?.includeUsage shouldBe true
        }
}
