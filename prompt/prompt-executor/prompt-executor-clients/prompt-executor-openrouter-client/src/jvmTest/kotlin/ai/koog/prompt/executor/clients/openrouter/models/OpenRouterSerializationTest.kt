package ai.koog.prompt.executor.clients.openrouter.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIFunction
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolCall
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolFunction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenRouterSerializationTest {

    private val requestJson = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    private val responseJson = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
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

        val jsonElement = requestJson.encodeToJsonElement(OpenRouterChatCompletionRequestSerializer, request)
        val jsonObject = jsonElement.jsonObject

        assertEquals("anthropic/claude-3-sonnet", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(0.7, jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(1000, jsonObject["maxTokens"]?.jsonPrimitive?.intOrNull)
        assertEquals(false, jsonObject["stream"]?.jsonPrimitive?.booleanOrNull)
        assertNull(jsonObject["customProperty"])
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

        val jsonElement = requestJson.encodeToJsonElement(OpenRouterChatCompletionRequestSerializer, request)
        val jsonObject = jsonElement.jsonObject

        // Standard properties should be present
        assertEquals("anthropic/claude-3-sonnet", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(0.7, jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull)

        // Additional properties should be flattened to root level
        assertEquals("customValue", jsonObject["customProperty"]?.jsonPrimitive?.contentOrNull)
        assertEquals(42, jsonObject["customNumber"]?.jsonPrimitive?.intOrNull)
        assertEquals(true, jsonObject["customBoolean"]?.jsonPrimitive?.booleanOrNull)

        // additionalProperties field itself should not be present in serialized JSON
        assertNull(jsonObject["additionalProperties"])
    }

    @Test
    fun `test deserialization without additional properties`() {
        val jsonInput = buildJsonObject {
            put("model", JsonPrimitive("anthropic/claude-3-sonnet"))
            put(
                "messages",
                buildJsonArray {
                    addJsonObject {
                        put("role", "user")
                        put("content", "Hello")
                    }
                }
            )
            put("temperature", JsonPrimitive(0.7))
            put("maxTokens", JsonPrimitive(1000))
            put("stream", JsonPrimitive(false))
        }

        val request = requestJson.decodeFromJsonElement(OpenRouterChatCompletionRequestSerializer, jsonInput)

        assertEquals("anthropic/claude-3-sonnet", request.model)
        assertEquals(0.7, request.temperature)
        assertEquals(1000, request.maxTokens)
        assertEquals(false, request.stream)
        assertNull(request.additionalProperties)
    }

    @Test
    fun `test deserialization with additional properties`() {
        val jsonInput = buildJsonObject {
            put("model", JsonPrimitive("anthropic/claude-3-sonnet"))
            put(
                "messages",
                buildJsonArray {
                    addJsonObject {
                        put("role", "user")
                        put("content", "Hello")
                    }
                }
            )
            put("temperature", JsonPrimitive(0.7))
            put("customProperty", JsonPrimitive("customValue"))
            put("customNumber", JsonPrimitive(42))
            put("customBoolean", JsonPrimitive(true))
        }

        val request = requestJson.decodeFromJsonElement(OpenRouterChatCompletionRequestSerializer, jsonInput)

        assertEquals("anthropic/claude-3-sonnet", request.model)
        assertEquals(0.7, request.temperature)

        assertNotNull(request.additionalProperties)
        val additionalProps = request.additionalProperties
        assertEquals(3, additionalProps.size)
        assertEquals("customValue", additionalProps["customProperty"]?.jsonPrimitive?.contentOrNull)
        assertEquals(42, additionalProps["customNumber"]?.jsonPrimitive?.intOrNull)
        assertEquals(true, additionalProps["customBoolean"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `test round trip serialization with additionalProperties`() {
        val originalAdditionalProperties = mapOf<String, JsonElement>(
            "customProperty" to JsonPrimitive("customValue"),
            "customNumber" to JsonPrimitive(42)
        )

        val originalRequest = OpenRouterChatCompletionRequest(
            model = "anthropic/claude-3-sonnet",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.7,
            additionalProperties = originalAdditionalProperties
        )

        // Serialize to JSON string
        val jsonString = requestJson.encodeToString(OpenRouterChatCompletionRequestSerializer, originalRequest)

        // Deserialize back to object
        val deserializedRequest = requestJson.decodeFromString(OpenRouterChatCompletionRequestSerializer, jsonString)

        // Verify standard properties
        assertEquals(originalRequest.model, deserializedRequest.model)
        assertEquals(originalRequest.temperature, deserializedRequest.temperature)
        assertEquals(originalRequest.messages.size, deserializedRequest.messages.size)

        // Verify additional properties were preserved
        assertNotNull(deserializedRequest.additionalProperties)
        val deserializedAdditionalProps = deserializedRequest.additionalProperties
        assertEquals(originalAdditionalProperties.size, deserializedAdditionalProps.size)
        assertEquals(
            originalAdditionalProperties["customProperty"]?.jsonPrimitive?.contentOrNull,
            deserializedAdditionalProps["customProperty"]?.jsonPrimitive?.contentOrNull
        )
        assertEquals(
            originalAdditionalProperties["customNumber"]?.jsonPrimitive?.intOrNull,
            deserializedAdditionalProps["customNumber"]?.jsonPrimitive?.intOrNull
        )
    }

    @Test
    fun `test OpenRouter response deserialization`() {
        val jsonInput = buildJsonObject {
            put("id", "gen-xxxxxxxxxxxxxx")
            put("created", 1699000000L)
            put("model", "openai/gpt-3.5-turbo")
            put("object", "chat.completion")
            put("system_fingerprint", "fp_44709d6fcb")
            put(
                "choices",
                buildJsonArray {
                    addJsonObject {
                        put("finish_reason", "stop")
                        put(
                            "message",
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", "Hello there!")
                            }
                        )
                    }
                }
            )
            put(
                "usage",
                buildJsonObject {
                    put("prompt_tokens", 10)
                    put("completion_tokens", 4)
                    put("total_tokens", 14)
                }
            )
        }

        val response = responseJson.decodeFromJsonElement(OpenRouterChatCompletionResponse.serializer(), jsonInput)

        assertEquals("gen-xxxxxxxxxxxxxx", response.id)
        assertEquals(1699000000L, response.created)
        assertEquals("openai/gpt-3.5-turbo", response.model)
        assertEquals("chat.completion", response.objectType)
        assertEquals("fp_44709d6fcb", response.systemFingerprint)

        assertEquals(1, response.choices.size)
        val choice = response.choices.first()
        assertEquals("stop", choice.finishReason)

        val message = choice.message as OpenAIMessage.Assistant
        assertEquals("Hello there!", message.content?.text())

        assertNotNull(response.usage)
        assertEquals(10, response.usage.promptTokens)
        assertEquals(4, response.usage.completionTokens)
        assertEquals(14, response.usage.totalTokens)
    }

    @Test
    fun `test OpenRouter error response deserialization`() {
        val jsonInput = buildJsonObject {
            put("id", "gen-error-test")
            put("created", 1699000000L)
            put("model", "openai/gpt-4")
            put("object", "chat.completion")
            put(
                "choices",
                buildJsonArray {
                    addJsonObject {
                        put("finish_reason", "error")
                        put("native_finish_reason", "content_filter")
                        put(
                            "message",
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", "")
                            }
                        )
                        put(
                            "error",
                            buildJsonObject {
                                put("code", 400)
                                put("message", "Content filtered due to policy violation")
                                put(
                                    "metadata",
                                    buildJsonObject {
                                        put("provider", "openai")
                                        put("raw_error", "content_filter")
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }

        val response = responseJson.decodeFromJsonElement(OpenRouterChatCompletionResponse.serializer(), jsonInput)

        val choice = response.choices.first()
        assertEquals("error", choice.finishReason)
        assertEquals("content_filter", choice.nativeFinishReason)

        assertNotNull(choice.error)
        assertEquals(400, choice.error.code)
        assertEquals("Content filtered due to policy violation", choice.error.message)
        assertNotNull(choice.error.metadata)
        assertEquals("openai", choice.error.metadata["provider"])
    }

    @Test
    fun `test OpenRouter streaming response deserialization`() {
        val jsonInput = buildJsonObject {
            put("id", "gen-stream-test")
            put("created", 1699000000L)
            put("model", "anthropic/claude-3-sonnet")
            put("object", "chat.completion.chunk")
            put(
                "choices",
                buildJsonArray {
                    addJsonObject {
                        put("finish_reason", null)
                        put("native_finish_reason", null)
                        put(
                            "delta",
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", "Hello")
                            }
                        )
                    }
                }
            )
        }

        val response = responseJson.decodeFromJsonElement(OpenRouterChatCompletionStreamResponse.serializer(), jsonInput)

        assertEquals("gen-stream-test", response.id)
        assertEquals("chat.completion.chunk", response.objectType)
        assertEquals(1, response.choices.size)

        val choice = response.choices.first()
        assertNull(choice.finishReason)
        assertNull(choice.nativeFinishReason)
        assertEquals("Hello", choice.delta.content)
        assertEquals("assistant", choice.delta.role)
    }

    @Test
    fun `test OpenRouter response with tool calls deserialization`() {
        val jsonInput = buildJsonObject {
            put("id", "gen-tool-call-test")
            put("created", 1699000000L)
            put("model", "openai/gpt-4")
            put("object", "chat.completion")
            put("system_fingerprint", "fp_44709d6fcb")
            put(
                "choices",
                buildJsonArray {
                    addJsonObject {
                        put("finish_reason", "tool_calls")
                        put(
                            "message",
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", null)
                                put(
                                    "tool_calls",
                                    buildJsonArray {
                                        addJsonObject {
                                            put("id", "call_abc123")
                                            put("type", "function")
                                            put(
                                                "function",
                                                buildJsonObject {
                                                    put("name", "get_current_weather")
                                                    put("arguments", "{\"location\": \"Boston, MA\"}")
                                                }
                                            )
                                        }
                                        addJsonObject {
                                            put("id", "call_def456")
                                            put("type", "function")
                                            put(
                                                "function",
                                                buildJsonObject {
                                                    put("name", "get_forecast")
                                                    put("arguments", "{\"location\": \"Boston, MA\", \"days\": 3}")
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
            put(
                "usage",
                buildJsonObject {
                    put("prompt_tokens", 82)
                    put("completion_tokens", 18)
                    put("total_tokens", 100)
                }
            )
        }

        val response = responseJson.decodeFromJsonElement(OpenRouterChatCompletionResponse.serializer(), jsonInput)

        assertEquals("gen-tool-call-test", response.id)
        assertEquals(1699000000L, response.created)
        assertEquals("openai/gpt-4", response.model)
        assertEquals("chat.completion", response.objectType)
        assertEquals("fp_44709d6fcb", response.systemFingerprint)

        assertEquals(1, response.choices.size)
        val choice = response.choices.first()
        assertEquals("tool_calls", choice.finishReason)

        val message = choice.message as OpenAIMessage.Assistant
        assertNull(message.content)
        assertNotNull(message.toolCalls)
        assertEquals(2, message.toolCalls!!.size)

        val firstToolCall = message.toolCalls!![0]
        assertEquals("call_abc123", firstToolCall.id)
        assertEquals("function", firstToolCall.type)
        assertEquals("get_current_weather", firstToolCall.function.name)
        assertEquals("{\"location\": \"Boston, MA\"}", firstToolCall.function.arguments)

        val secondToolCall = message.toolCalls!![1]
        assertEquals("call_def456", secondToolCall.id)
        assertEquals("function", secondToolCall.type)
        assertEquals("get_forecast", secondToolCall.function.name)
        assertEquals("{\"location\": \"Boston, MA\", \"days\": 3}", secondToolCall.function.arguments)

        assertNotNull(response.usage)
        assertEquals(82, response.usage.promptTokens)
        assertEquals(18, response.usage.completionTokens)
        assertEquals(100, response.usage.totalTokens)
    }

    @Test
    fun `test OpenRouter streaming response with tool calls deserialization`() {
        val jsonInput = buildJsonObject {
            put("id", "gen-stream-tool-test")
            put("created", 1699000000L)
            put("model", "openai/gpt-4")
            put("object", "chat.completion.chunk")
            put(
                "choices",
                buildJsonArray {
                    addJsonObject {
                        put("finish_reason", null)
                        put("native_finish_reason", null)
                        put(
                            "delta",
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", null)
                                put(
                                    "tool_calls",
                                    buildJsonArray {
                                        addJsonObject {
                                            put("id", "call_xyz789")
                                            put("type", "function")
                                            put(
                                                "function",
                                                buildJsonObject {
                                                    put("name", "calculate_total")
                                                    put("arguments", "{\"items\": [")
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }

        val response = responseJson.decodeFromJsonElement(OpenRouterChatCompletionStreamResponse.serializer(), jsonInput)

        assertEquals("gen-stream-tool-test", response.id)
        assertEquals("chat.completion.chunk", response.objectType)
        assertEquals(1, response.choices.size)

        val choice = response.choices.first()
        assertNull(choice.finishReason)
        assertNull(choice.nativeFinishReason)
        assertNull(choice.delta.content)
        assertEquals("assistant", choice.delta.role)

        assertNotNull(choice.delta.toolCalls)
        assertEquals(1, choice.delta.toolCalls.size)

        val toolCall = choice.delta.toolCalls[0]
        assertEquals("call_xyz789", toolCall.id)
        assertEquals("function", toolCall.type)
        assertEquals("calculate_total", toolCall.function.name)
        assertEquals("{\"items\": [", toolCall.function.arguments)
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

        val jsonElement = responseJson.encodeToJsonElement(OpenRouterChatCompletionRequestSerializer, request)
        val jsonObject = jsonElement.jsonObject

        assertEquals("openai/gpt-4", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertNotNull(jsonObject["messages"])
        assertNotNull(jsonObject["tools"])
        assertEquals("auto", jsonObject["tool_choice"]?.jsonPrimitive?.contentOrNull)

        // Verify the serialized messages include tool calls
        val messages = jsonObject["messages"]!!.jsonArray
        assertEquals(3, messages.size)

        // Check assistant message with tool calls
        val assistantMessage = messages[1].jsonObject
        assertEquals("assistant", assistantMessage["role"]?.jsonPrimitive?.contentOrNull)
        assertNotNull(assistantMessage["tool_calls"])
        val toolCalls = assistantMessage["tool_calls"]!!.jsonArray
        assertEquals(1, toolCalls.size)

        val toolCall = toolCalls[0].jsonObject
        assertEquals("call_abc123", toolCall["id"]?.jsonPrimitive?.contentOrNull)
        assertEquals("function", toolCall["type"]?.jsonPrimitive?.contentOrNull)

        val function = toolCall["function"]!!.jsonObject
        assertEquals("get_current_weather", function["name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("{\"location\": \"Boston, MA\"}", function["arguments"]?.jsonPrimitive?.contentOrNull)

        // Check tool message
        val toolMessage = messages[2].jsonObject
        assertEquals("tool", toolMessage["role"]?.jsonPrimitive?.contentOrNull)
        assertEquals("The weather in Boston is 72°F and sunny", toolMessage["content"]?.jsonPrimitive?.contentOrNull)
        assertEquals("call_abc123", toolMessage["tool_call_id"]?.jsonPrimitive?.contentOrNull)
    }
}
