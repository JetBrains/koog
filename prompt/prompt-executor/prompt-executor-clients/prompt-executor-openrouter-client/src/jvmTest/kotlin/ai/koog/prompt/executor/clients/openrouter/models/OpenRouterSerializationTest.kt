package ai.koog.prompt.executor.clients.openrouter.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIFunction
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolCall
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolFunction
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class OpenRouterSerializationTest {

    companion object {

        /**
         * OpenRouter-specific JSON configuration with snake_case naming strategy (lenient mode).
         * This configuration ignores unknown keys for more flexible deserialization.
         * Snake_case is required since OpenRouter is OpenAI-compatible and uses snake_case field names.
         */
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
            encodeDefaults = true
            namingStrategy = JsonNamingStrategy.SnakeCase
        }
    }

    @Test
    fun `test serialization without additionalProperties`() {
        val request = OpenRouterChatCompletionRequest(
            model = "anthropic/claude-3-sonnet",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.7,
            maxTokens = 1000,
            stream = false
        )

        val jsonString = json.encodeToString(OpenRouterChatCompletionRequestSerializer, request)

        jsonString shouldEqualJson
            // language=json
            """
            {
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "model": "anthropic/claude-3-sonnet",
                "stream": false,
                "temperature": 0.7,
                "max_tokens": 1000
            }
            """.trimIndent()
    }

    @Test
    fun `test serialization with additionalProperties`() {
        val additionalProperties = mapOf<String, JsonElement>(
            "customProperty" to JsonPrimitive("customValue"),
            "customNumber" to JsonPrimitive(42),
            "customBoolean" to JsonPrimitive(true)
        )

        val request = OpenRouterChatCompletionRequest(
            model = "anthropic/claude-3-sonnet",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.7,
            additionalProperties = additionalProperties
        )

        val jsonString = json.encodeToString(OpenRouterChatCompletionRequestSerializer, request)

        jsonString shouldEqualJson
            // language=json
            """
            {
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "model": "anthropic/claude-3-sonnet",
                "temperature": 0.7,
                "additional_properties": {
                    "customProperty": "customValue",
                    "customNumber": 42,
                    "customBoolean": true
                }
            }
            """.trimIndent()
    }

    @Test
    fun `test deserialization without additional properties`() {
        val jsonString =
            // language=json
            """
            {
                "model": "anthropic/claude-3-sonnet",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.7,
                "stream": false
            }
            """.trimIndent()

        val request = json.decodeFromString(OpenRouterChatCompletionRequestSerializer, jsonString)

        request.model shouldBe "anthropic/claude-3-sonnet"
        request.temperature shouldBe 0.7
        request.stream shouldBe false
        request.additionalProperties shouldBe null
    }

    @Test
    fun `test deserialization with additional properties`() {
        val jsonString =
            // language=json
            """
            {
                "model": "anthropic/claude-3-sonnet",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.7,
                "extra": "value",
                "number": 42,
                "flag": true
            }
            """.trimIndent()

        val request = json.decodeFromString(OpenRouterChatCompletionRequestSerializer, jsonString)

        // Verify basic deserialization works
        request.model shouldBe "anthropic/claude-3-sonnet"
        request.temperature shouldBe 0.7

        // Note: Additional properties functionality is currently broken with JsonNamingStrategy.SnakeCase
        // due to AdditionalPropertiesFlatteningSerializer bug:
        // KG-531 OpenRouter's AdditionalPropertiesFlatteningSerializer is incompatible with JsonNamingStrategy.SnakeCase
        // In lenient mode, unknown properties
        // are ignored instead of collected.
        //
        // The test passes because ignoreUnknownKeys = true, but additional properties are not captured
        request.additionalProperties shouldBe null
    }

    @Test
    fun `test OpenRouter response deserialization`() {
        val jsonString =
            // language=json
            """
            {
                "id": "gen-xxxxxxxxxxxxxx",
                "created": 1699000000,
                "model": "openai/gpt-3.5-turbo",
                "object": "chat.completion",
                "system_fingerprint": "fp_44709d6fcb",
                "choices": [
                    {
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": "Hello there!"
                        }
                    }
                ],
                "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 4,
                    "total_tokens": 14
                }
            }
            """.trimIndent()

        val response = json.decodeFromString(OpenRouterChatCompletionResponse.serializer(), jsonString)

        response.id shouldBe "gen-xxxxxxxxxxxxxx"
        response.created shouldBe 1699000000L
        response.model shouldBe "openai/gpt-3.5-turbo"
        response.objectType shouldBe "chat.completion"
        response.systemFingerprint shouldBe "fp_44709d6fcb"

        response.choices.size shouldBe 1
        val choice = response.choices.first()
        choice.finishReason shouldBe "stop"

        val message = choice.message as OpenAIMessage.Assistant
        message.content?.text() shouldBe "Hello there!"

        response.usage shouldNotBe null
        response.usage?.promptTokens shouldBe 10
        response.usage?.completionTokens shouldBe 4
        response.usage?.totalTokens shouldBe 14
    }

    @Test
    fun `test OpenRouter error response deserialization`() {
        val jsonString =
            // language=json
            """
            {
                "id": "gen-error-test",
                "created": 1699000000,
                "model": "openai/gpt-4",
                "object": "chat.completion",
                "choices": [
                    {
                        "finish_reason": "error",
                        "native_finish_reason": "content_filter",
                        "message": {
                            "role": "assistant",
                            "content": ""
                        },
                        "error": {
                            "code": 400,
                            "message": "Content filtered due to policy violation",
                            "metadata": {
                                "provider": "openai",
                                "raw_error": "content_filter"
                            }
                        }
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString(OpenRouterChatCompletionResponse.serializer(), jsonString)

        val choice = response.choices.first()
        choice.finishReason shouldBe "error"
        choice.nativeFinishReason shouldBe "content_filter"

        choice.error shouldNotBe null
        choice.error?.code shouldBe 400
        choice.error?.message shouldBe "Content filtered due to policy violation"
        choice.error?.metadata shouldNotBe null
        choice.error?.metadata?.get("provider") shouldBe "openai"
    }

    @Test
    fun `test OpenRouter streaming response deserialization`() {
        val jsonString =
            // language=json
            """
            {
                "id": "gen-stream-test",
                "created": 1699000000,
                "model": "anthropic/claude-3-sonnet",
                "object": "chat.completion.chunk",
                "choices": [
                    {
                        "finish_reason": null,
                        "native_finish_reason": null,
                        "delta": {
                            "role": "assistant",
                            "content": "Hello"
                        }
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString(OpenRouterChatCompletionStreamResponse.serializer(), jsonString)

        response.id shouldBe "gen-stream-test"
        response.objectType shouldBe "chat.completion.chunk"
        response.choices.size shouldBe 1

        val choice = response.choices.first()
        choice.finishReason shouldBe null
        choice.nativeFinishReason shouldBe null
        choice.delta.content shouldBe "Hello"
        choice.delta.role shouldBe "assistant"
    }

    @Test
    fun `test OpenRouter response with tool calls deserialization`() {
        val jsonString =
            // language=json
            """
            {
                "id": "gen-tool-call-test",
                "created": 1699000000,
                "model": "openai/gpt-4",
                "object": "chat.completion",
                "system_fingerprint": "fp_44709d6fcb",
                "choices": [
                    {
                        "finish_reason": "tool_calls",
                        "message": {
                            "role": "assistant",
                            "content": null,
                            "tool_calls": [
                                {
                                    "id": "call_abc123",
                                    "type": "function",
                                    "function": {
                                        "name": "get_current_weather",
                                        "arguments": "{\"location\": \"Boston, MA\"}"
                                    }
                                },
                                {
                                    "id": "call_def456",
                                    "type": "function",
                                    "function": {
                                        "name": "get_forecast",
                                        "arguments": "{\"location\": \"Boston, MA\", \"days\": 3}"
                                    }
                                }
                            ]
                        }
                    }
                ],
                "usage": {
                    "prompt_tokens": 82,
                    "completion_tokens": 18,
                    "total_tokens": 100
                }
            }
            """.trimIndent()

        val response = json.decodeFromString(OpenRouterChatCompletionResponse.serializer(), jsonString)

        response.id shouldBe "gen-tool-call-test"
        response.created shouldBe 1699000000L
        response.model shouldBe "openai/gpt-4"
        response.objectType shouldBe "chat.completion"
        response.systemFingerprint shouldBe "fp_44709d6fcb"

        response.choices.size shouldBe 1
        val choice = response.choices.first()
        choice.finishReason shouldBe "tool_calls"

        val message = choice.message as OpenAIMessage.Assistant
        message.content shouldBe null
        message.toolCalls shouldNotBe null
        message.toolCalls!!.size shouldBe 2

        val firstToolCall = message.toolCalls!![0]
        firstToolCall.id shouldBe "call_abc123"
        firstToolCall.type shouldBe "function"
        firstToolCall.function.name shouldBe "get_current_weather"
        firstToolCall.function.arguments shouldBe "{\"location\": \"Boston, MA\"}"

        val secondToolCall = message.toolCalls!![1]
        secondToolCall.id shouldBe "call_def456"
        secondToolCall.type shouldBe "function"
        secondToolCall.function.name shouldBe "get_forecast"
        secondToolCall.function.arguments shouldBe "{\"location\": \"Boston, MA\", \"days\": 3}"

        response.usage shouldNotBe null
        response.usage?.promptTokens shouldBe 82
        response.usage?.completionTokens shouldBe 18
        response.usage?.totalTokens shouldBe 100
    }

    @Test
    fun `test OpenRouter streaming response with tool calls deserialization`() {
        val jsonString =
            // language=json
            """
            {
                "id": "gen-stream-tool-test",
                "created": 1699000000,
                "model": "openai/gpt-4",
                "object": "chat.completion.chunk",
                "choices": [
                    {
                        "finish_reason": null,
                        "native_finish_reason": null,
                        "delta": {
                            "role": "assistant",
                            "content": null,
                            "tool_calls": [
                                {
                                    "id": "call_xyz789",
                                    "type": "function",
                                    "function": {
                                        "name": "calculate_total",
                                        "arguments": "{\"items\": ["
                                    }
                                }
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString(OpenRouterChatCompletionStreamResponse.serializer(), jsonString)

        response.id shouldBe "gen-stream-tool-test"
        response.objectType shouldBe "chat.completion.chunk"
        response.choices.size shouldBe 1

        val choice = response.choices.first()
        choice.finishReason shouldBe null
        choice.nativeFinishReason shouldBe null
        choice.delta.content shouldBe null
        choice.delta.role shouldBe "assistant"

        choice.delta.toolCalls shouldNotBe null
        choice.delta.toolCalls?.size shouldBe 1

        val toolCall = choice.delta.toolCalls?.get(0)!!
        toolCall.id shouldBe "call_xyz789"
        toolCall.type shouldBe "function"
        toolCall.function.name shouldBe "calculate_total"
        toolCall.function.arguments shouldBe "{\"items\": ["
    }

    @Test
    fun `test OpenRouter request with tools serialization`() {
        val tools = listOf(
            OpenAITool(
                function = OpenAIToolFunction(
                    name = "get_current_weather",
                    description = "Get the current weather in a given location",
                    parameters = buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "location",
                                    buildJsonObject {
                                        put("type", "string")
                                        put("description", "The city and state, e.g. San Francisco, CA")
                                    }
                                )
                                put(
                                    "unit",
                                    buildJsonObject {
                                        put("type", "string")
                                        put(
                                            "enum",
                                            buildJsonArray {
                                                add("celsius")
                                                add("fahrenheit")
                                            }
                                        )
                                    }
                                )
                            }
                        )
                        put("required", buildJsonArray { add("location") })
                    }
                )
            )
        )

        val request = OpenRouterChatCompletionRequest(
            model = "openai/gpt-4",
            messages = listOf(
                OpenAIMessage.User(content = Content.Text("What's the weather like in Boston?")),
                OpenAIMessage.Assistant(
                    content = null,
                    toolCalls = listOf(
                        OpenAIToolCall(
                            id = "call_abc123",
                            function = OpenAIFunction(
                                name = "get_current_weather",
                                arguments = "{\"location\": \"Boston, MA\"}"
                            )
                        )
                    )
                ),
                OpenAIMessage.Tool(
                    content = Content.Text("The weather in Boston is 72°F and sunny"),
                    toolCallId = "call_abc123"
                )
            ),
            tools = tools,
            toolChoice = OpenAIToolChoice.Auto
        )

        val jsonString = json.encodeToString(OpenRouterChatCompletionRequestSerializer, request)

        jsonString shouldEqualJson
            // language=json
            """
            {
                "messages": [
                    {
                        "role": "user",
                        "content": "What's the weather like in Boston?"
                    },
                    {
                        "role": "assistant",
                        "tool_calls": [
                            {
                                "id": "call_abc123",
                                "function": {
                                    "name": "get_current_weather",
                                    "arguments": "{\"location\": \"Boston, MA\"}"
                                },
                                "type": "function"
                            }
                        ]
                    },
                    {
                        "role": "tool",
                        "content": "The weather in Boston is 72°F and sunny",
                        "tool_call_id": "call_abc123"
                    }
                ],
                "model": "openai/gpt-4",
                "tools": [
                    {
                        "function": {
                            "name": "get_current_weather",
                            "description": "Get the current weather in a given location",
                            "parameters": {
                                "type": "object",
                                "properties": {
                                    "location": {
                                        "type": "string",
                                        "description": "The city and state, e.g. San Francisco, CA"
                                    },
                                    "unit": {
                                        "type": "string",
                                        "enum": ["celsius", "fahrenheit"]
                                    }
                                },
                                "required": ["location"]
                            }
                        },
                        "type": "function"
                    }
                ],
                "tool_choice": "auto"
            }
            """.trimIndent()
    }

    @Test
    fun `test all fields serialization`() {
        val request = OpenRouterChatCompletionRequest(
            model = "openai/gpt-4",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hi"))),
            temperature = 0.4,
            topP = 0.9,
            topLogprobs = 3,
            maxTokens = 128,
            frequencyPenalty = 0.1,
            presencePenalty = -0.2,
            stop = listOf("END", "STOP"),
            logprobs = true,
            topK = 5,
            repetitionPenalty = 1.1,
            minP = 0.05,
            topA = 0.2,
            prediction = OpenAIStaticContent(Content.Text("draft")),
            transforms = listOf("middle-out"),
            models = listOf("openai/gpt-4", "anthropic/claude-3-sonnet"),
            route = "my-route",
            provider = ProviderPreferences(
                order = listOf("openai", "anthropic"),
                allowFallbacks = true,
                requireParameters = true,
                dataCollection = "allow",
                only = listOf("openai"),
                ignore = listOf("google"),
                quantizations = listOf("int4"),
                sort = "price",
                maxPrice = mapOf("prompt" to "0.002", "completion" to "0.006")
            ),
            user = "user-123",
            stream = false,
            additionalProperties = mapOf<String, JsonElement>(
                "x-extra" to JsonPrimitive("ok")
            )
        )

        val jsonString = json.encodeToString(OpenRouterChatCompletionRequestSerializer, request)

        jsonString shouldEqualJson
            // language=json
            """
            {
                "messages": [
                    {
                        "role": "user",
                        "content": "Hi"
                    }
                ],
                "model": "openai/gpt-4",
                "stream": false,
                "temperature": 0.4,
                "top_p": 0.9,
                "top_logprobs": 3,
                "max_tokens": 128,
                "frequency_penalty": 0.1,
                "presence_penalty": -0.2,
                "stop": ["END", "STOP"],
                "logprobs": true,
                "top_k": 5,
                "repetition_penalty": 1.1,
                "min_p": 0.05,
                "top_a": 0.2,
                "prediction": {
                    "content": "draft",
                    "type": "content"
                },
                "transforms": ["middle-out"],
                "models": ["openai/gpt-4", "anthropic/claude-3-sonnet"],
                "route": "my-route",
                "provider": {
                    "order": ["openai", "anthropic"],
                    "allow_fallbacks": true,
                    "require_parameters": true,
                    "data_collection": "allow",
                    "only": ["openai"],
                    "ignore": ["google"],
                    "quantizations": ["int4"],
                    "sort": "price",
                    "max_price": {
                        "prompt": "0.002",
                        "completion": "0.006"
                    }
                },
                "user": "user-123",
                "additional_properties": {
                    "x-extra": "ok"
                }
            }
            """.trimIndent()
    }

    @Test
    fun `test all fields deserialization`() {
        val jsonString =
            // language=json
            """
            {
                "model": "openai/gpt-4",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hi"
                    }
                ],
                "stream": false,
                "temperature": 0.4,
                "top_p": 0.9,
                "top_logprobs": 3,
                "max_tokens": 128,
                "frequency_penalty": 0.1,
                "presence_penalty": -0.2,
                "stop": ["END", "STOP"],
                "logprobs": true,
                "top_k": 5,
                "repetition_penalty": 1.1,
                "min_p": 0.05,
                "top_a": 0.2,
                "prediction": {
                    "type": "content",
                    "content": "draft"
                },
                "transforms": ["middle-out"],
                "models": ["openai/gpt-4", "anthropic/claude-3-sonnet"],
                "route": "my-route",
                "provider": {
                    "order": ["openai", "anthropic"],
                    "allow_fallbacks": true,
                    "require_parameters": true,
                    "data_collection": "allow",
                    "only": ["openai"],
                    "ignore": ["google"],
                    "quantizations": ["int4"],
                    "sort": "price",
                    "max_price": {
                        "prompt": "0.002",
                        "completion": "0.006"
                    }
                },
                "user": "user-123"
            }
            """.trimIndent()

        // Test with standard serializer to isolate AdditionalPropertiesFlatteningSerializer issues
        // The AdditionalPropertiesFlatteningSerializer has a bug with snake_case naming strategy
        // where all snake_case properties are incorrectly classified as additional properties:
        // KG-531 OpenRouter's AdditionalPropertiesFlatteningSerializer is incompatible with JsonNamingStrategy.SnakeCase
        val req = json.decodeFromString(OpenRouterChatCompletionRequest.serializer(), jsonString)

        req.model shouldBe "openai/gpt-4"
        req.temperature shouldBe 0.4
        req.topP shouldBe 0.9
        req.topLogprobs shouldBe 3
        req.maxTokens shouldBe 128
        req.frequencyPenalty shouldBe 0.1
        req.presencePenalty shouldBe -0.2
        req.logprobs shouldBe true
        req.topK shouldBe 5
        req.repetitionPenalty shouldBe 1.1
        req.minP shouldBe 0.05
        req.topA shouldBe 0.2
        req.stop shouldBe listOf("END", "STOP")
        req.prediction shouldNotBe null
        req.prediction?.content?.text() shouldBe "draft"
        req.transforms shouldBe listOf("middle-out")
        req.models shouldBe listOf("openai/gpt-4", "anthropic/claude-3-sonnet")
        req.route shouldBe "my-route"
        req.user shouldBe "user-123"
        req.provider shouldNotBe null
        req.provider?.order shouldBe listOf("openai", "anthropic")
        req.provider?.allowFallbacks shouldBe true
        req.provider?.requireParameters shouldBe true
        req.provider?.dataCollection shouldBe "allow"
        req.provider?.only shouldBe listOf("openai")
        req.provider?.ignore shouldBe listOf("google")
        req.provider?.quantizations shouldBe listOf("int4")
        req.provider?.sort shouldBe "price"
        req.provider?.maxPrice shouldBe mapOf("prompt" to "0.002", "completion" to "0.006")
    }
}
