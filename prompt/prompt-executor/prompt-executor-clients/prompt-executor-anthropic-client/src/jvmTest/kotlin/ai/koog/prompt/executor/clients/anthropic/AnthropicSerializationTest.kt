package ai.koog.prompt.executor.clients.anthropic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnthropicSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // Ensure default values are included in serialization
        explicitNulls = false
    }

    @Test
    fun `test serialization without additionalProperties`() {
        val request = AnthropicMessageRequest(
            model = "claude-3",
            messages = listOf(
                AnthropicMessage(
                    role = "user",
                    content = listOf(AnthropicContent.Text("Hello"))
                )
            ),
            maxTokens = 1000,
            temperature = 0.7
        )

        val jsonElement = json.encodeToJsonElement(AnthropicMessageRequestSerializer, request)
        val jsonObject = jsonElement.jsonObject

        assertEquals("claude-3", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1000, jsonObject["maxTokens"]?.jsonPrimitive?.intOrNull)
        assertEquals(0.7, jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull)
        assertNull(jsonObject["customProperty"])
    }

    @Test
    fun `test serialization with additionalProperties`() {
        val additionalProperties = mapOf<String, JsonElement>(
            "customProperty" to JsonPrimitive("customValue"),
            "customNumber" to JsonPrimitive(42),
            "customBoolean" to JsonPrimitive(true)
        )

        val request = AnthropicMessageRequest(
            model = "claude-3",
            messages = listOf(
                AnthropicMessage(
                    role = "user",
                    content = listOf(AnthropicContent.Text("Hello"))
                )
            ),
            maxTokens = 1000,
            additionalProperties = additionalProperties
        )

        val jsonElement = json.encodeToJsonElement(AnthropicMessageRequestSerializer, request)
        val jsonObject = jsonElement.jsonObject

        // Standard properties should be present
        assertEquals("claude-3", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1000, jsonObject["maxTokens"]?.jsonPrimitive?.intOrNull)

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
            put("model", JsonPrimitive("claude-3"))
            put(
                "messages",
                json.encodeToJsonElement(
                    listOf(
                        AnthropicMessage(
                            role = "user",
                            content = listOf(AnthropicContent.Text("Hello"))
                        )
                    )
                )
            )
            put("maxTokens", JsonPrimitive(1000))
            put("temperature", JsonPrimitive(0.7))
        }

        val request = json.decodeFromJsonElement<AnthropicMessageRequest>(jsonInput)

        assertEquals("claude-3", request.model)
        assertEquals(1000, request.maxTokens)
        assertEquals(0.7, request.temperature)
        assertNull(request.additionalProperties)
    }

    @Test
    fun `test deserialization with additional properties`() {
        val jsonInput = buildJsonObject {
            put("model", JsonPrimitive("claude-3"))
            put(
                "messages",
                json.encodeToJsonElement(
                    listOf(
                        AnthropicMessage(
                            role = "user",
                            content = listOf(AnthropicContent.Text("Hello"))
                        )
                    )
                )
            )
            put("maxTokens", JsonPrimitive(1000))
            put("customProperty", JsonPrimitive("customValue"))
            put("customNumber", JsonPrimitive(42))
            put("customBoolean", JsonPrimitive(true))
        }

        val request = json.decodeFromJsonElement(AnthropicMessageRequestSerializer, jsonInput)

        assertEquals("claude-3", request.model)
        assertEquals(1000, request.maxTokens)

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

        val originalRequest = AnthropicMessageRequest(
            model = "claude-3",
            messages = listOf(
                AnthropicMessage(
                    role = "user",
                    content = listOf(AnthropicContent.Text("Hello"))
                )
            ),
            maxTokens = 1000,
            additionalProperties = originalAdditionalProperties
        )

        // Serialize to JSON string
        val jsonString = json.encodeToString(AnthropicMessageRequestSerializer, originalRequest)

        // Deserialize back to object
        val deserializedRequest = json.decodeFromString(AnthropicMessageRequestSerializer, jsonString)

        // Verify standard properties
        assertEquals(originalRequest.model, deserializedRequest.model)
        assertEquals(originalRequest.maxTokens, deserializedRequest.maxTokens)
        assertEquals(originalRequest.messages.size, deserializedRequest.messages.size)

        // Verify additional properties were preserved
        assertNotNull(deserializedRequest.additionalProperties)
        val deserializedAdditionalProps = deserializedRequest.additionalProperties
        assertEquals(originalAdditionalProperties.size, deserializedAdditionalProps.size)
        assertEquals(
            (originalAdditionalProperties["customProperty"] as JsonPrimitive).content,
            (deserializedAdditionalProps["customProperty"] as JsonPrimitive).content
        )
        assertEquals(
            originalAdditionalProperties["customNumber"]?.jsonPrimitive?.intOrNull,
            deserializedAdditionalProps["customNumber"]?.jsonPrimitive?.intOrNull
        )
    }
}
