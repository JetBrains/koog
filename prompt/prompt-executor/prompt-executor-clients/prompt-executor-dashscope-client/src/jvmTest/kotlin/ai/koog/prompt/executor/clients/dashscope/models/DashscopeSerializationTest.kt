package ai.koog.prompt.executor.clients.dashscope.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

        val jsonElement = json.encodeToJsonElement(DashscopeChatCompletionRequest.serializer(), request)
        val jsonObject = jsonElement.jsonObject

        assertEquals("qwen-plus", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(0.7, jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(1000, jsonObject["maxTokens"]?.jsonPrimitive?.intOrNull)
        assertEquals(false, jsonObject["stream"]?.jsonPrimitive?.booleanOrNull)
        assertNull(jsonObject["enableSearch"])
        assertNull(jsonObject["parallelToolCalls"])
        assertNull(jsonObject["enableThinking"])
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

        val jsonElement = json.encodeToJsonElement(DashscopeChatCompletionRequest.serializer(), request)
        val jsonObject = jsonElement.jsonObject

        assertEquals("qwen-plus", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(0.8, jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(true, jsonObject["enableSearch"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(false, jsonObject["parallelToolCalls"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, jsonObject["enableThinking"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(0.5, jsonObject["frequencyPenalty"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(0.3, jsonObject["presencePenalty"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(true, jsonObject["logprobs"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(5, jsonObject["topLogprobs"]?.jsonPrimitive?.intOrNull)
        assertEquals(0.9, jsonObject["topP"]?.jsonPrimitive?.doubleOrNull)
        assertNotNull(jsonObject["stop"])
    }

    @Test
    fun `test deserialization without DashScope-specific fields`() {
        val jsonInput = buildJsonObject {
            put("model", JsonPrimitive("qwen-max"))
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

        val request = json.decodeFromJsonElement(DashscopeChatCompletionRequest.serializer(), jsonInput)

        assertEquals("qwen-max", request.model)
        assertEquals(0.7, request.temperature)
        assertEquals(1000, request.maxTokens)
        assertEquals(false, request.stream)
        assertNull(request.enableSearch)
        assertNull(request.parallelToolCalls)
        assertNull(request.enableThinking)
    }

    @Test
    fun `test deserialization with DashScope-specific fields`() {
        val jsonInput = buildJsonObject {
            put("model", JsonPrimitive("qwen-long"))
            put(
                "messages",
                buildJsonArray {
                    addJsonObject {
                        put("role", "user")
                        put("content", "Test message")
                    }
                }
            )
            put("temperature", JsonPrimitive(0.5))
            put("enableSearch", JsonPrimitive(true))
            put("parallelToolCalls", JsonPrimitive(false))
            put("enableThinking", JsonPrimitive(true))
            put("frequencyPenalty", JsonPrimitive(0.2))
            put("presencePenalty", JsonPrimitive(0.1))
            put("logprobs", JsonPrimitive(true))
            put("topLogprobs", JsonPrimitive(3))
            put("topP", JsonPrimitive(0.95))
            put(
                "stop",
                buildJsonArray {
                    add(JsonPrimitive("STOP"))
                    add(JsonPrimitive("END"))
                }
            )
        }

        val request = json.decodeFromJsonElement(DashscopeChatCompletionRequest.serializer(), jsonInput)

        assertEquals("qwen-long", request.model)
        assertEquals(0.5, request.temperature)
        assertEquals(true, request.enableSearch)
        assertEquals(false, request.parallelToolCalls)
        assertEquals(true, request.enableThinking)
        assertEquals(0.2, request.frequencyPenalty)
        assertEquals(0.1, request.presencePenalty)
        assertEquals(true, request.logprobs)
        assertEquals(3, request.topLogprobs)
        assertEquals(0.95, request.topP)
        assertEquals(listOf("STOP", "END"), request.stop)
    }

    @Test
    fun `test round trip serialization`() {
        val originalRequest = DashscopeChatCompletionRequest(
            model = "qwen-plus",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Test round trip"))),
            temperature = 0.6,
            maxTokens = 500,
            enableSearch = false,
            parallelToolCalls = true,
            enableThinking = false,
            frequencyPenalty = 1.0,
            presencePenalty = -1.0,
            logprobs = false,
            topP = 0.8,
            stop = listOf("END")
        )

        // Serialize to JSON string
        val jsonString = json.encodeToString(DashscopeChatCompletionRequest.serializer(), originalRequest)

        // Deserialize back to object
        val deserializedRequest = json.decodeFromString(DashscopeChatCompletionRequest.serializer(), jsonString)

        // Verify all properties were preserved
        assertEquals(originalRequest.model, deserializedRequest.model)
        assertEquals(originalRequest.temperature, deserializedRequest.temperature)
        assertEquals(originalRequest.maxTokens, deserializedRequest.maxTokens)
        assertEquals(originalRequest.enableSearch, deserializedRequest.enableSearch)
        assertEquals(originalRequest.parallelToolCalls, deserializedRequest.parallelToolCalls)
        assertEquals(originalRequest.enableThinking, deserializedRequest.enableThinking)
        assertEquals(originalRequest.frequencyPenalty, deserializedRequest.frequencyPenalty)
        assertEquals(originalRequest.presencePenalty, deserializedRequest.presencePenalty)
        assertEquals(originalRequest.logprobs, deserializedRequest.logprobs)
        assertEquals(originalRequest.topP, deserializedRequest.topP)
        assertEquals(originalRequest.stop, deserializedRequest.stop)
        assertEquals(originalRequest.messages.size, deserializedRequest.messages.size)
    }

    @Test
    fun `test chat completion response deserialization with systemFingerprint`() {
        val jsonInput = buildJsonObject {
            put("id", JsonPrimitive("chatcmpl-123"))
            put("object", JsonPrimitive("chat.completion"))
            put("created", JsonPrimitive(1677652288))
            put("model", JsonPrimitive("qwen-plus"))
            put("systemFingerprint", JsonNull)
            put(
                "choices",
                buildJsonArray {
                    addJsonObject {
                        put("index", JsonPrimitive(0))
                        put("finishReason", JsonPrimitive("stop"))
                        put(
                            "message",
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", "Hello! How can I help you?")
                            }
                        )
                    }
                }
            )
            put(
                "usage",
                buildJsonObject {
                    put("promptTokens", JsonPrimitive(10))
                    put("completionTokens", JsonPrimitive(20))
                    put("totalTokens", JsonPrimitive(30))
                }
            )
        }

        val response = json.decodeFromJsonElement(DashscopeChatCompletionResponse.serializer(), jsonInput)

        assertEquals("chatcmpl-123", response.id)
        assertEquals("qwen-plus", response.model)
        assertEquals(1677652288L, response.created)
        assertEquals(null, response.systemFingerprint)
        assertEquals("chat.completion", response.objectType)
        assertEquals(1, response.choices.size)
        assertNotNull(response.usage)
        assertEquals(10, response.usage?.promptTokens)
        assertEquals(20, response.usage?.completionTokens)
        assertEquals(30, response.usage?.totalTokens)
    }

    @Test
    fun `test chat completion response deserialization without systemFingerprint`() {
        val jsonInput = buildJsonObject {
            put("id", JsonPrimitive("chatcmpl-456"))
            put("object", JsonPrimitive("chat.completion"))
            put("created", JsonPrimitive(1677652300))
            put("model", JsonPrimitive("qwen-max"))
            put(
                "choices",
                buildJsonArray {
                    addJsonObject {
                        put("index", JsonPrimitive(0))
                        put("finishReason", JsonPrimitive("stop"))
                        put(
                            "message",
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", "Test response")
                            }
                        )
                    }
                }
            )
        }

        val response = json.decodeFromJsonElement(DashscopeChatCompletionResponse.serializer(), jsonInput)

        assertEquals("chatcmpl-456", response.id)
        assertEquals("qwen-max", response.model)
        assertEquals(1677652300L, response.created)
        assertNull(response.systemFingerprint)
        assertEquals("chat.completion", response.objectType)
        assertEquals(1, response.choices.size)
        assertNull(response.usage)
    }

    @Test
    fun `test chat completion stream response deserialization with systemFingerprint`() {
        val jsonInput = buildJsonObject {
            put("id", JsonPrimitive("chatcmpl-789"))
            put("object", JsonPrimitive("chat.completion.chunk"))
            put("created", JsonPrimitive(1677652400))
            put("model", JsonPrimitive("qwen-turbo"))
            put("systemFingerprint", JsonNull)
            put(
                "choices",
                buildJsonArray {
                    addJsonObject {
                        put("index", JsonPrimitive(0))
                        put("finishReason", JsonPrimitive(null as String?))
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

        val response = json.decodeFromJsonElement(DashscopeChatCompletionStreamResponse.serializer(), jsonInput)

        assertEquals("chatcmpl-789", response.id)
        assertEquals("qwen-turbo", response.model)
        assertEquals(1677652400L, response.created)
        assertNull(response.systemFingerprint)
        assertEquals("chat.completion.chunk", response.objectType)
        assertEquals(1, response.choices.size)
        assertNull(response.usage)
    }

    @Test
    fun `test chat completion stream response deserialization without systemFingerprint`() {
        val jsonInput = buildJsonObject {
            put("id", JsonPrimitive("chatcmpl-012"))
            put("object", JsonPrimitive("chat.completion.chunk"))
            put("created", JsonPrimitive(1677652500))
            put("model", JsonPrimitive("qwen-long"))
            put(
                "choices",
                buildJsonArray {
                    addJsonObject {
                        put("index", JsonPrimitive(0))
                        put("finishReason", JsonPrimitive("stop"))
                        put(
                            "delta",
                            buildJsonObject {
                                put("content", "Final chunk")
                            }
                        )
                    }
                }
            )
            put(
                "usage",
                buildJsonObject {
                    put("promptTokens", JsonPrimitive(15))
                    put("completionTokens", JsonPrimitive(25))
                    put("totalTokens", JsonPrimitive(40))
                }
            )
        }

        val response = json.decodeFromJsonElement(DashscopeChatCompletionStreamResponse.serializer(), jsonInput)

        assertEquals("chatcmpl-012", response.id)
        assertEquals("qwen-long", response.model)
        assertEquals(1677652500L, response.created)
        assertNull(response.systemFingerprint)
        assertEquals("chat.completion.chunk", response.objectType)
        assertEquals(1, response.choices.size)
        assertNotNull(response.usage)
        assertEquals(15, response.usage?.promptTokens)
        assertEquals(25, response.usage?.completionTokens)
        assertEquals(40, response.usage?.totalTokens)
    }
}
