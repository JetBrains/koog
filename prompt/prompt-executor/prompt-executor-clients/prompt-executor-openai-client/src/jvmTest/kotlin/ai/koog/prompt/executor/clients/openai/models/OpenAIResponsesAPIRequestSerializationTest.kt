package ai.koog.prompt.executor.clients.openai.models

import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.base.models.ServiceTier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
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

    @Test
    fun `test full serialization of OpenAIResponsesAPIRequest fields`() {
        val request = OpenAIResponsesAPIRequest(
            model = "gpt-4o",
            instructions = "sys-msg",
            temperature = 0.5,
            maxOutputTokens = 321,
            stream = false,
            background = true,
            include = listOf(OpenAIInclude.OUTPUT_TEXT_LOGPROBS, OpenAIInclude.REASONING_ENCRYPTED_CONTENT),
            maxToolCalls = 7,
            parallelToolCalls = true,
            reasoning = ReasoningConfig(effort = ReasoningEffort.HIGH, summary = ReasoningSummary.CONCISE),
            truncation = Truncation.AUTO,
            promptCacheKey = "pck",
            safetyIdentifier = "sid",
            serviceTier = ServiceTier.FLEX,
            store = true,
            topLogprobs = 5,
            topP = 0.9,
            user = "user-123",
            additionalProperties = mapOf("extra" to JsonPrimitive("value"))
        )

        val json = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }
        val jsonObject = json.encodeToJsonElement(OpenAIResponsesAPIRequestSerializer, request).jsonObject

        assertEquals("gpt-4o", jsonObject["model"]?.jsonPrimitive?.content)
        assertEquals("sys-msg", jsonObject["instructions"]?.jsonPrimitive?.content)
        assertEquals(0.5, jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(321, jsonObject["maxOutputTokens"]?.jsonPrimitive?.intOrNull)
        assertEquals(false, jsonObject["stream"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, jsonObject["background"]?.jsonPrimitive?.booleanOrNull)

        val includeArray = (jsonObject["include"] as JsonArray)
        val include = includeArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("message.output_text.logprobs", "reasoning.encrypted_content"), include)

        assertEquals(7, jsonObject["maxToolCalls"]?.jsonPrimitive?.intOrNull)
        assertEquals(true, jsonObject["parallelToolCalls"]?.jsonPrimitive?.booleanOrNull)

        val reasoning = jsonObject["reasoning"]!!.jsonObject
        assertEquals("high", reasoning["effort"]?.jsonPrimitive?.content)
        assertEquals("concise", reasoning["summary"]?.jsonPrimitive?.content)

        assertEquals("auto", jsonObject["truncation"]?.jsonPrimitive?.content)
        assertEquals("pck", jsonObject["promptCacheKey"]?.jsonPrimitive?.content)
        assertEquals("sid", jsonObject["safetyIdentifier"]?.jsonPrimitive?.content)
        assertEquals("flex", jsonObject["serviceTier"]?.jsonPrimitive?.content)
        assertEquals(true, jsonObject["store"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(5, jsonObject["topLogprobs"]?.jsonPrimitive?.intOrNull)
        assertEquals(0.9, jsonObject["topP"]?.jsonPrimitive?.doubleOrNull)
        assertEquals("user-123", jsonObject["user"]?.jsonPrimitive?.content)
        assertEquals("value", jsonObject["extra"]?.jsonPrimitive?.content)

        // Ensure additionalProperties is flattened
        assertNull(jsonObject["additionalProperties"])
    }

    @Test
    fun `test full deserialization of OpenAIResponsesAPIRequest fields`() {
        val json = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }
        val input = buildJsonObject {
            put("model", JsonPrimitive("gpt-4o"))
            put("instructions", JsonPrimitive("sys-msg"))
            put("temperature", JsonPrimitive(0.5))
            put("maxOutputTokens", JsonPrimitive(321))
            put("stream", JsonPrimitive(false))
            put("background", JsonPrimitive(true))
            put(
                "include",
                buildJsonArray {
                    add(JsonPrimitive("message.output_text.logprobs"))
                    add(JsonPrimitive("reasoning.encrypted_content"))
                }
            )
            put("maxToolCalls", JsonPrimitive(7))
            put("parallelToolCalls", JsonPrimitive(true))
            put(
                "reasoning",
                buildJsonObject {
                    put("effort", JsonPrimitive("high"))
                    put("summary", JsonPrimitive("concise"))
                }
            )
            put("truncation", JsonPrimitive("auto"))
            put("promptCacheKey", JsonPrimitive("pck"))
            put("safetyIdentifier", JsonPrimitive("sid"))
            put("serviceTier", JsonPrimitive("flex"))
            put("store", JsonPrimitive(true))
            put("topLogprobs", JsonPrimitive(5))
            put("topP", JsonPrimitive(0.9))
            put("user", JsonPrimitive("user-123"))
            // additional flattened custom fields
            put("extra", JsonPrimitive("value"))
            put("customNumber", JsonPrimitive(42))
        }

        val decoded = json.decodeFromJsonElement(OpenAIResponsesAPIRequestSerializer, input)

        assertEquals("gpt-4o", decoded.model)
        assertEquals("sys-msg", decoded.instructions)
        assertEquals(0.5, decoded.temperature)
        assertEquals(321, decoded.maxOutputTokens)
        assertEquals(false, decoded.stream)
        assertEquals(true, decoded.background)
        assertEquals(
            listOf(OpenAIInclude.OUTPUT_TEXT_LOGPROBS, OpenAIInclude.REASONING_ENCRYPTED_CONTENT),
            decoded.include
        )
        assertEquals(7, decoded.maxToolCalls)
        assertEquals(true, decoded.parallelToolCalls)
        assertEquals(Truncation.AUTO, decoded.truncation)
        assertEquals("pck", decoded.promptCacheKey)
        assertEquals("sid", decoded.safetyIdentifier)
        assertEquals(ServiceTier.FLEX, decoded.serviceTier)
        assertEquals(true, decoded.store)
        assertEquals(5, decoded.topLogprobs)
        assertEquals(0.9, decoded.topP)
        assertEquals("user-123", decoded.user)

        assertNotNull(decoded.reasoning)
        assertEquals(ReasoningEffort.HIGH, decoded.reasoning.effort)
        assertEquals(ReasoningSummary.CONCISE, decoded.reasoning.summary)

        assertNotNull(decoded.additionalProperties)
        assertEquals("value", decoded.additionalProperties.get("extra")?.jsonPrimitive?.content)
        assertEquals(42, decoded.additionalProperties.get("customNumber")?.jsonPrimitive?.intOrNull)
    }
}
