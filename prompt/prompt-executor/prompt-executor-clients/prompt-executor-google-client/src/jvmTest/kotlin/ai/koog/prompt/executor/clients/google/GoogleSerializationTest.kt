package ai.koog.prompt.executor.clients.google

import ai.koog.test.utils.verifyDeserialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GoogleSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    @Test
    fun `test serialization without additionalProperties`() {
        val request = GoogleGenerationConfig(
            responseMimeType = "application/json",
            maxOutputTokens = 1000,
            temperature = 0.7,
            candidateCount = 1,
            topP = 0.9,
            topK = 40
        )

        val jsonElement = json.encodeToJsonElement(request)
        val jsonObject = jsonElement.jsonObject

        assertEquals("application/json", jsonObject["responseMimeType"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1000, jsonObject["maxOutputTokens"]?.jsonPrimitive?.intOrNull)
        assertEquals(0.7, jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(1, jsonObject["candidateCount"]?.jsonPrimitive?.intOrNull)
        assertEquals(0.9, jsonObject["topP"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(40, jsonObject["topK"]?.jsonPrimitive?.intOrNull)
        assertNull(jsonObject["customProperty"])
    }

    @Test
    fun `test serialization with additionalProperties`() {
        val additionalProperties = mapOf<String, JsonElement>(
            "customProperty" to JsonPrimitive("customValue"),
            "customNumber" to JsonPrimitive(42),
            "customBoolean" to JsonPrimitive(true)
        )

        val generationConfig = GoogleGenerationConfig(
            responseMimeType = "application/json",
            maxOutputTokens = 1000,
            temperature = 0.7,
            additionalProperties = additionalProperties
        )
        val request = GoogleRequest(contents = emptyList(), generationConfig = generationConfig)

        val jsonElement = json.encodeToJsonElement(request)
        val jsonObject = jsonElement.jsonObject["generationConfig"]!!.jsonObject

        // Standard properties should be present
        assertEquals("application/json", jsonObject["responseMimeType"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1000, jsonObject["maxOutputTokens"]?.jsonPrimitive?.intOrNull)
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
        val jsonString =
            // language=json
            """
            {
                "responseMimeType": "application/json",
                "maxOutputTokens": 1000,
                "temperature": 0.7,
                "candidateCount": 1,
                "topP": 0.9,
                "topK": 40
            }
            """.trimIndent()

        val request: GoogleGenerationConfig = verifyDeserialization(
            payload = jsonString,
            json = json
        )

        assertEquals("application/json", request.responseMimeType)
        assertEquals(1000, request.maxOutputTokens)
        assertEquals(0.7, request.temperature)
        assertEquals(1, request.candidateCount)
        assertEquals(0.9, request.topP)
        assertEquals(40, request.topK)
        assertNull(request.additionalProperties)
    }

    @Test
    fun `test deserialization with additional properties`() {
        val jsonString =
            // language=json
            """ {
                "contents": [
                   {
                     "role": "user",
                     "parts": [{"text": "Hello"}]
                   }
               ], 
                "generationConfig": {
                    "responseMimeType": "application/json",
                    "maxOutputTokens": 1000,
                    "temperature": 0.7,
                    "candidateCount": 1,
                    "topP": 0.9,
                    "topK": 40,
                    "customProperty": "customValue",
                    "customNumber": 42,
                    "customBoolean": true
                }
            }
            """.trimIndent()

        val request: GoogleRequest = verifyDeserialization(
            payload = jsonString,
            json = json
        )
        val generationConfig = request.generationConfig!!

        assertEquals("application/json", generationConfig.responseMimeType)
        assertEquals(1000, generationConfig.maxOutputTokens)
        assertEquals(0.7, generationConfig.temperature)

        assertNotNull(generationConfig.additionalProperties)
        val additionalProps = generationConfig.additionalProperties
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

        val originalRequest = GoogleGenerationConfig(
            responseMimeType = "application/json",
            maxOutputTokens = 1000,
            temperature = 0.7,
            additionalProperties = originalAdditionalProperties
        )

        // Serialize to JSON string
        val jsonString = json.encodeToString(originalRequest)

        // Deserialize back to object
        val deserializedRequest = json.decodeFromString<GoogleGenerationConfig>(jsonString)

        // Verify standard properties
        assertEquals(originalRequest.responseMimeType, deserializedRequest.responseMimeType)
        assertEquals(originalRequest.maxOutputTokens, deserializedRequest.maxOutputTokens)
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
