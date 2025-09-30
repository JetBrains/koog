package ai.koog.prompt.executor.clients.openai.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenAIResponsesAPIRequestSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    @Test
    fun `test serialization without additionalProperties`() {
        val request = OpenAIResponsesAPIRequest(
            model = "gpt-4o",
            instructions = "Please help with this task",
            temperature = 0.7,
            maxOutputTokens = 1000,
            stream = false
        )

        val jsonElement = json.encodeToJsonElement(OpenAIResponsesAPIRequestSerializer, request)
        val jsonObject = jsonElement.jsonObject

        assertEquals("gpt-4o", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Please help with this task", jsonObject["instructions"]?.jsonPrimitive?.contentOrNull)
        assertEquals(0.7, jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(1000, jsonObject["maxOutputTokens"]?.jsonPrimitive?.intOrNull)
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

        val request = OpenAIResponsesAPIRequest(
            model = "gpt-4o",
            instructions = "Please help with this task",
            temperature = 0.7,
            additionalProperties = additionalProperties
        )

        val jsonElement = json.encodeToJsonElement(OpenAIResponsesAPIRequestSerializer, request)
        val jsonObject = jsonElement.jsonObject

        // Standard properties should be present
        assertEquals("gpt-4o", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Please help with this task", jsonObject["instructions"]?.jsonPrimitive?.contentOrNull)
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
            put("model", JsonPrimitive("gpt-4o"))
            put("instructions", JsonPrimitive("Please help with this task"))
            put("temperature", JsonPrimitive(0.7))
            put("maxOutputTokens", JsonPrimitive(1000))
            put("stream", JsonPrimitive(false))
        }

        val request = json.decodeFromJsonElement(OpenAIResponsesAPIRequestSerializer, jsonInput)

        assertEquals("gpt-4o", request.model)
        assertEquals("Please help with this task", request.instructions)
        assertEquals(0.7, request.temperature)
        assertEquals(1000, request.maxOutputTokens)
        assertEquals(false, request.stream)
        assertNull(request.additionalProperties)
    }

    @Test
    fun `test deserialization with additional properties`() {
        val jsonInput = buildJsonObject {
            put("model", JsonPrimitive("gpt-4o"))
            put("instructions", JsonPrimitive("Please help with this task"))
            put("temperature", JsonPrimitive(0.7))
            put("customProperty", JsonPrimitive("customValue"))
            put("customNumber", JsonPrimitive(42))
            put("customBoolean", JsonPrimitive(true))
        }

        val request = json.decodeFromJsonElement(OpenAIResponsesAPIRequestSerializer, jsonInput)

        assertEquals("gpt-4o", request.model)
        assertEquals("Please help with this task", request.instructions)
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

        val originalRequest = OpenAIResponsesAPIRequest(
            model = "gpt-4o",
            instructions = "Please help with this task",
            temperature = 0.7,
            additionalProperties = originalAdditionalProperties
        )

        // Serialize to JSON string
        val jsonString = json.encodeToString(OpenAIResponsesAPIRequestSerializer, originalRequest)

        // Deserialize back to object
        val deserializedRequest = json.decodeFromString(OpenAIResponsesAPIRequestSerializer, jsonString)

        // Verify standard properties
        assertEquals(originalRequest.model, deserializedRequest.model)
        assertEquals(originalRequest.instructions, deserializedRequest.instructions)
        assertEquals(originalRequest.temperature, deserializedRequest.temperature)

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
