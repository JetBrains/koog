package ai.koog.prompt.executor.clients.anthropic

import ai.koog.test.utils.verifyDeserialization
import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
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
                    content = listOf(AnthropicContent.Text("Hello, Claude"))
                )
            ),
            maxTokens = 1000,
            temperature = 0.7
        )

        val jsonString = json.encodeToString(AnthropicMessageRequestSerializer, request)

        jsonString shouldEqualJson
            // language=json
            """
            {
                "model": "claude-3",
                "max_tokens": 1000,
                "messages": [
                    {"role": "user", "content": [{ "type": "text", "text": "Hello, Claude"}]}
                ],
                "temperature": 0.7,
                "stream": false
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
        assertEquals(1000, jsonObject["max_tokens"]?.jsonPrimitive?.intOrNull)

        // Additional properties should be flattened to root level
        assertEquals("customValue", jsonObject["customProperty"]?.jsonPrimitive?.contentOrNull)
        assertEquals(42, jsonObject["customNumber"]?.jsonPrimitive?.intOrNull)
        assertEquals(true, jsonObject["customBoolean"]?.jsonPrimitive?.booleanOrNull)

        // additionalProperties field itself should not be present in serialized JSON
        assertNull(jsonObject["additionalProperties"])
    }

    @Test
    fun `test deserialization without additional properties`() {
        val jsonString =
            // language=json
            """
            {
                "model": "claude-3",
                "max_tokens": 1000,
                "messages": [
                    {"role": "user", "content": [{ "type": "text", "text": "Hello, Claude"}]}
                ],
                "temperature": 0.7,
                "stream": false
            }
            """.trimIndent()

        val request: AnthropicMessageRequest = verifyDeserialization(
            payload = jsonString,
            serializer = AnthropicMessageRequestSerializer,
            json = json
        )

        assertEquals("claude-3", request.model)
        assertEquals(1000, request.maxTokens)
        assertEquals(0.7, request.temperature)
        assertNull(request.additionalProperties)
    }

    @Test
    fun `test deserialization with additional properties`() {
        val jsonString =
            // language=json
            """
            {
                "model": "claude-3",
                "max_tokens": 1000,
                "messages": [
                    {"role": "user", "content": [{ "type": "text", "text": "Hello, Claude"}]}
                ],
                "temperature": 0.7,
                "stream": false,
                "customProperty": "customValue",
                "customNumber": 42,
                "customBoolean": true
            }
            """.trimIndent()

        val request: AnthropicMessageRequest = verifyDeserialization(
            payload = jsonString,
            serializer = AnthropicMessageRequestSerializer,
            json = json
        )

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
