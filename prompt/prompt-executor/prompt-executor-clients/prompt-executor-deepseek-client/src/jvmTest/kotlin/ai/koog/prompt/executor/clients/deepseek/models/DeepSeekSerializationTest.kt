package ai.koog.prompt.executor.clients.deepseek.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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

class DeepSeekSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    @Test
    fun `test serialization without additionalProperties`() {
        val request = DeepSeekChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.7,
            maxTokens = 1000,
            stream = false
        )

        val jsonElement = json.encodeToJsonElement(DeepSeekChatCompletionRequestSerializer, request)
        val jsonObject = jsonElement.jsonObject

        assertEquals("deepseek-chat", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
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

        val request = DeepSeekChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.7,
            additionalProperties = additionalProperties
        )

        val jsonElement = json.encodeToJsonElement(DeepSeekChatCompletionRequestSerializer, request)
        val jsonObject = jsonElement.jsonObject

        // Standard properties should be present
        assertEquals("deepseek-chat", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
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
            put("model", JsonPrimitive("deepseek-chat"))
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

        val request = json.decodeFromJsonElement(DeepSeekChatCompletionRequestSerializer, jsonInput)

        assertEquals("deepseek-chat", request.model)
        assertEquals(0.7, request.temperature)
        assertEquals(1000, request.maxTokens)
        assertEquals(false, request.stream)
        assertNull(request.additionalProperties)
    }

    @Test
    fun `test deserialization with additional properties`() {
        val jsonInput = buildJsonObject {
            put("model", JsonPrimitive("deepseek-chat"))
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

        val request = json.decodeFromJsonElement(DeepSeekChatCompletionRequestSerializer, jsonInput)

        assertEquals("deepseek-chat", request.model)
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

        val originalRequest = DeepSeekChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.7,
            additionalProperties = originalAdditionalProperties
        )

        // Serialize to JSON string
        val jsonString = json.encodeToString(DeepSeekChatCompletionRequestSerializer, originalRequest)

        // Deserialize back to object
        val deserializedRequest = json.decodeFromString(DeepSeekChatCompletionRequestSerializer, jsonString)

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
}
