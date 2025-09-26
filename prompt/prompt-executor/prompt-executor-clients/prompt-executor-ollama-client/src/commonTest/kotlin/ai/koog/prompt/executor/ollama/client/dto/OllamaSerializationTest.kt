package ai.koog.prompt.executor.ollama.client.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OllamaSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    @Test
    fun `test serialization without additionalProperties`() {
        val request = OllamaChatRequestDTO(
            model = "llama2",
            messages = listOf(
                OllamaChatMessageDTO(
                    role = "user",
                    content = "Hello"
                )
            ),
            stream = false,
            options = OllamaChatRequestDTO.Options(temperature = 0.7)
        )

        val jsonElement = json.encodeToJsonElement(OllamaChatRequestDTOSerializer, request)
        val jsonObject = jsonElement.jsonObject

        assertEquals("llama2", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(false, jsonObject["stream"]?.jsonPrimitive?.booleanOrNull)
        assertNotNull(jsonObject["options"])
        assertNull(jsonObject["customProperty"])
    }

    @Test
    fun `test serialization with additionalProperties`() {
        val additionalProperties = mapOf<String, JsonElement>(
            "customProperty" to JsonPrimitive("customValue"),
            "customNumber" to JsonPrimitive(42),
            "customBoolean" to JsonPrimitive(true)
        )

        val request = OllamaChatRequestDTO(
            model = "llama2",
            messages = listOf(
                OllamaChatMessageDTO(
                    role = "user",
                    content = "Hello"
                )
            ),
            stream = false,
            additionalProperties = additionalProperties
        )

        val jsonElement = json.encodeToJsonElement(OllamaChatRequestDTOSerializer, request)
        val jsonObject = jsonElement.jsonObject

        // Standard properties should be present
        assertEquals("llama2", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(false, jsonObject["stream"]?.jsonPrimitive?.booleanOrNull)

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
            put("model", JsonPrimitive("llama2"))
            put(
                "messages",
                json.encodeToJsonElement(
                    listOf(
                        OllamaChatMessageDTO(
                            role = "user",
                            content = "Hello"
                        )
                    )
                )
            )
            put("stream", JsonPrimitive(false))
        }

        val request = json.decodeFromJsonElement(OllamaChatRequestDTOSerializer, jsonInput)

        assertEquals("llama2", request.model)
        assertEquals(false, request.stream)
        assertNull(request.additionalProperties)
    }

    @Test
    fun `test deserialization with additional properties`() {
        val jsonInput = buildJsonObject {
            put("model", JsonPrimitive("llama2"))
            put(
                "messages",
                json.encodeToJsonElement(
                    listOf(
                        OllamaChatMessageDTO(
                            role = "user",
                            content = "Hello"
                        )
                    )
                )
            )
            put("stream", JsonPrimitive(false))
            put("customProperty", JsonPrimitive("customValue"))
            put("customNumber", JsonPrimitive(42))
            put("customBoolean", JsonPrimitive(true))
        }

        val request = json.decodeFromJsonElement(OllamaChatRequestDTOSerializer, jsonInput)

        assertEquals("llama2", request.model)
        assertEquals(false, request.stream)

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

        val originalRequest = OllamaChatRequestDTO(
            model = "llama2",
            messages = listOf(
                OllamaChatMessageDTO(
                    role = "user",
                    content = "Hello"
                )
            ),
            stream = false,
            additionalProperties = originalAdditionalProperties
        )

        // Serialize to JSON string
        val jsonString = json.encodeToString(OllamaChatRequestDTOSerializer, originalRequest)

        // Deserialize back to object
        val deserializedRequest = json.decodeFromString(OllamaChatRequestDTOSerializer, jsonString)

        // Verify standard properties
        assertEquals(originalRequest.model, deserializedRequest.model)
        assertEquals(originalRequest.stream, deserializedRequest.stream)
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
