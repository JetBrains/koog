package ai.koog.prompt.executor.clients.siliconflow.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIFunction
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolCall
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolFunction
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class SiliconFlowSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test
    fun testDocumentContractSerializeBasicRequest() {
        val request = SiliconFlowChatCompletionRequest(
            model = "Pro/deepseek-ai/DeepSeek-R1",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            stream = false,
            temperature = 0.7,
            maxTokens = 128,
            topP = 0.9,
            topK = 40,
            frequencyPenalty = 0.2,
            stop = listOf("END")
        )

        val jsonString = json.encodeToString(SiliconFlowChatCompletionRequestSerializer, request)

        jsonString shouldEqualJson
            // language=json
            """
            {
              "messages": [{"role": "user", "content": "Hello"}],
              "model": "Pro/deepseek-ai/DeepSeek-R1",
              "stream": false,
              "temperature": 0.7,
              "max_tokens": 128,
              "top_p": 0.9,
              "top_k": 40,
              "frequency_penalty": 0.2,
              "stop": ["END"]
            }
            """.trimIndent()
    }

    @Test
    fun testDocumentContractSerializeResponseFormatJsonObject() {
        val request = SiliconFlowChatCompletionRequest(
            model = "Pro/deepseek-ai/DeepSeek-R1",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Return JSON"))),
            responseFormat = OpenAIResponseFormat.JsonObject()
        )

        val jsonString = json.encodeToString(SiliconFlowChatCompletionRequestSerializer, request)

        jsonString shouldEqualJson
            // language=json
            """
            {
              "messages": [{"role": "user", "content": "Return JSON"}],
              "model": "Pro/deepseek-ai/DeepSeek-R1",
              "response_format": {"type": "json_object"}
            }
            """.trimIndent()
    }

    @Test
    fun testDocumentContractSerializeToolsAndToolChoice() {
        val tools = listOf(
            OpenAITool(
                function = OpenAIToolFunction(
                    name = "get_current_weather",
                    description = "Get current weather",
                    parameters = buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject {
                                put("city", buildJsonObject { put("type", "string") })
                            }
                        )
                        put("required", buildJsonArray { add("city") })
                    }
                )
            )
        )

        val request = SiliconFlowChatCompletionRequest(
            model = "Pro/deepseek-ai/DeepSeek-R1",
            messages = listOf(
                OpenAIMessage.User(content = Content.Text("Weather in Boston")),
                OpenAIMessage.Assistant(
                    toolCalls = listOf(
                        OpenAIToolCall(
                            id = "call_1",
                            function = OpenAIFunction("get_current_weather", "{\"city\":\"Boston\"}")
                        )
                    )
                )
            ),
            tools = tools,
            toolChoice = OpenAIToolChoice.Auto
        )

        val jsonString = json.encodeToString(SiliconFlowChatCompletionRequestSerializer, request)

        jsonString shouldEqualJson
            // language=json
            """
            {
              "messages": [
                {"role": "user", "content": "Weather in Boston"},
                {
                  "role": "assistant",
                  "tool_calls": [
                    {
                      "id": "call_1",
                      "function": {"name": "get_current_weather", "arguments": "{\"city\":\"Boston\"}"},
                      "type": "function"
                    }
                  ]
                }
              ],
              "model": "Pro/deepseek-ai/DeepSeek-R1",
              "tools": [
                {
                  "function": {
                    "name": "get_current_weather",
                    "description": "Get current weather",
                    "parameters": {
                      "type": "object",
                      "properties": {"city": {"type": "string"}},
                      "required": ["city"]
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
    fun testDocumentContractDeserializeChatCompletionResponse() {
        val jsonString =
            // language=json
            """
            {
              "id": "chatcmpl-1",
              "created": 1699000000,
              "model": "Pro/deepseek-ai/DeepSeek-R1",
              "object": "chat.completion",
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

        val response = json.decodeFromString(SiliconFlowChatCompletionResponse.serializer(), jsonString)

        response.id shouldBe "chatcmpl-1"
        response.created shouldBe 1699000000L
        response.objectType shouldBe "chat.completion"
        response.choices.size shouldBe 1

        val choice = response.choices.first()
        choice.finishReason shouldBe "stop"
        val message = choice.message as OpenAIMessage.Assistant
        message.content?.text() shouldBe "Hello there!"
        response.usage?.promptTokens shouldBe 10
        response.usage?.completionTokens shouldBe 4
        response.usage?.totalTokens shouldBe 14
    }

    @Test
    fun testDocumentContractDeserializeStreamChunkResponse() {
        val jsonString =
            // language=json
            """
            {
              "id": "chatcmpl-stream-1",
              "created": 1699000001,
              "model": "Pro/deepseek-ai/DeepSeek-R1",
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

        val response = json.decodeFromString(SiliconFlowChatCompletionStreamResponse.serializer(), jsonString)

        response.id shouldBe "chatcmpl-stream-1"
        response.objectType shouldBe "chat.completion.chunk"
        response.choices.size shouldBe 1
        response.choices.first().delta.role shouldBe "assistant"
        response.choices.first().delta.content shouldBe "Hello"
    }

    @Test
    fun testDocumentContractDeserializeChoiceErrorResponse() {
        val jsonString =
            // language=json
            """
            {
              "id": "chatcmpl-error-choice",
              "created": 1699000000,
              "model": "Pro/deepseek-ai/DeepSeek-R1",
              "object": "chat.completion",
              "choices": [
                {
                  "finish_reason": "error",
                  "native_finish_reason": "content_filter",
                  "message": {"role": "assistant", "content": ""},
                  "error": {
                    "code": 400,
                    "message": "Content filtered",
                    "metadata": {"provider": "openai"}
                  }
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString(SiliconFlowChatCompletionResponse.serializer(), jsonString)
        val choice = response.choices.first()

        choice.finishReason shouldBe "error"
        choice.nativeFinishReason shouldBe "content_filter"
        choice.error shouldNotBe null
        choice.error?.code shouldBe 400
        choice.error?.message shouldBe "Content filtered"
        choice.error?.metadata?.get("provider") shouldBe "openai"
    }

    @Test
    fun testImplementationCompatibilityDeserializeReasoningContent() {
        val jsonString =
            // language=json
            """
            {
              "id": "chatcmpl-reasoning",
              "created": 1699000002,
              "model": "Pro/deepseek-ai/DeepSeek-R1",
              "object": "chat.completion",
              "choices": [
                {
                  "finish_reason": "tool_calls",
                  "message": {
                    "role": "assistant",
                    "content": "",
                    "reasoning_content": "I should call the weather tool first.",
                    "tool_calls": [
                      {
                        "id": "call_weather",
                        "type": "function",
                        "function": {
                          "name": "weather",
                          "arguments": "{\"city\":\"Boston\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString(SiliconFlowChatCompletionResponse.serializer(), jsonString)
        val message = response.choices.first().message as OpenAIMessage.Assistant

        message.reasoningContent shouldBe "I should call the weather tool first."
        message.toolCalls shouldNotBe null
        message.toolCalls?.size shouldBe 1
        message.toolCalls?.first()?.function?.name shouldBe "weather"
    }

    @Test
    fun testImplementationCompatibilityDeserializeAllExtendedFieldsWithStandardSerializer() {
        val jsonString =
            // language=json
            """
            {
              "model": "Pro/deepseek-ai/DeepSeek-R1",
              "messages": [{"role": "user", "content": "Hi"}],
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
              "prediction": {"type": "content", "content": "draft"},
              "transforms": ["middle-out"],
              "models": ["Pro/deepseek-ai/DeepSeek-R1"],
              "route": "my-route",
              "provider": {
                "order": ["deepseek"],
                "allow_fallbacks": true,
                "require_parameters": true,
                "data_collection": "allow",
                "only": ["openai"],
                "ignore": ["google"],
                "quantizations": ["int4"],
                "sort": "price",
                "max_price": {"prompt": "0.002", "completion": "0.006"}
              },
              "user": "user-123"
            }
            """.trimIndent()

        val req = json.decodeFromString(SiliconFlowChatCompletionRequest.serializer(), jsonString)

        req.model shouldBe "Pro/deepseek-ai/DeepSeek-R1"
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
        req.models shouldBe listOf("Pro/deepseek-ai/DeepSeek-R1")
        req.route shouldBe "my-route"
        req.user shouldBe "user-123"
        req.provider shouldNotBe null
        req.provider?.order shouldBe listOf("deepseek")
        req.provider?.allowFallbacks shouldBe true
        req.provider?.requireParameters shouldBe true
        req.provider?.dataCollection shouldBe "allow"
        req.provider?.only shouldBe listOf("openai")
        req.provider?.ignore shouldBe listOf("google")
        req.provider?.quantizations shouldBe listOf("int4")
        req.provider?.sort shouldBe "price"
        req.provider?.maxPrice shouldBe mapOf("prompt" to "0.002", "completion" to "0.006")
    }

    @Test
    fun testImplementationCompatibilitySnakeCaseAdditionalPropertiesKnownLimitation() {
        val jsonString =
            // language=json
            """
            {
              "model": "Pro/deepseek-ai/DeepSeek-R1",
              "messages": [{"role": "user", "content": "Hello"}],
              "temperature": 0.7,
              "extra": "value",
              "number": 42
            }
            """.trimIndent()

        val request = json.decodeFromString(SiliconFlowChatCompletionRequestSerializer, jsonString)

        request.model shouldBe "Pro/deepseek-ai/DeepSeek-R1"
        request.temperature shouldBe 0.7
        request.additionalProperties shouldBe null
    }

    @Test
    fun testSerializeAdditionalPropertiesAsCurrentImplementation() {
        val request = SiliconFlowChatCompletionRequest(
            model = "Pro/deepseek-ai/DeepSeek-R1",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            prediction = OpenAIStaticContent(Content.Text("draft")),
            additionalProperties = mapOf(
                "customProperty" to JsonPrimitive("customValue"),
                "customNumber" to JsonPrimitive(42)
            )
        )

        val jsonString = json.encodeToString(SiliconFlowChatCompletionRequestSerializer, request)

        jsonString shouldEqualJson
            // language=json
            """
            {
              "messages": [{"role": "user", "content": "Hello"}],
              "model": "Pro/deepseek-ai/DeepSeek-R1",
              "prediction": {"content": "draft", "type": "content"},
              "additional_properties": {
                "customProperty": "customValue",
                "customNumber": 42
              }
            }
            """.trimIndent()
    }
}
