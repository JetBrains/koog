package ai.koog.prompt.executor.clients.openai.models

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/**
 * Reproduces the snake_case serialization path used by [OpenAILLMClient] to guard the fix
 * for an additional-properties leak in the outbound request body.
 */
class OpenAIRequestSnakeCaseSerializationTest {

    private val snakeCaseJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test
    fun `responses request flattens additionalProperties under snake_case naming`() {
        // Regression for #1878: with JsonNamingStrategy.SnakeCase the additionalProperties field
        // was being emitted verbatim as `additional_properties` at the top level of the request,
        // which OpenAI rejects with `400 unknown_parameter`.
        val request = OpenAIResponsesAPIRequest(
            model = "gpt-4o",
            instructions = "Please help with this task",
            temperature = 0.7,
            additionalProperties = mapOf(
                "reasoning" to buildJsonObject { put("effort", JsonPrimitive("none")) },
            ),
        )

        val encoded = snakeCaseJson.encodeToString(OpenAIResponsesAPIRequestSerializer, request)
        val tree = snakeCaseJson.parseToJsonElement(encoded).jsonObject

        tree.keys.shouldNotContain("additional_properties")
        tree.keys.shouldNotContain("additionalProperties")
        tree.keys.shouldContain("reasoning")

        tree["model"]?.jsonPrimitive?.content shouldBe "gpt-4o"
        tree["reasoning"].shouldNotBeNull().jsonObject["effort"]?.jsonPrimitive?.content shouldBe "none"
    }

    @Test
    fun `responses request preserves snake_case for native fields when flattening`() {
        val request = OpenAIResponsesAPIRequest(
            model = "gpt-4o",
            maxOutputTokens = 256,
            parallelToolCalls = true,
            additionalProperties = mapOf("custom_extra" to JsonPrimitive("value")),
        )

        snakeCaseJson.encodeToString(OpenAIResponsesAPIRequestSerializer, request) shouldEqualJson
            """
            {
                "model": "gpt-4o",
                "max_output_tokens": 256,
                "parallel_tool_calls": true,
                "custom_extra": "value"
            }
            """.trimIndent()
    }

    @Test
    fun `chat completion request flattens additionalProperties under snake_case naming`() {
        val request = OpenAIChatCompletionRequest(
            model = "gpt-4o",
            messages = emptyList(),
            additionalProperties = mapOf(
                "reasoning" to buildJsonObject { put("effort", JsonPrimitive("none")) },
            ),
        )

        val encoded = snakeCaseJson.encodeToString(OpenAIChatCompletionRequestSerializer, request)
        val tree = snakeCaseJson.parseToJsonElement(encoded).jsonObject

        tree.keys.shouldNotContain("additional_properties")
        tree.keys.shouldNotContain("additionalProperties")
        tree["reasoning"].shouldNotBeNull().jsonObject["effort"]?.jsonPrimitive?.content shouldBe "none"
    }

    @Test
    fun `responses request round trip with snake_case naming preserves additional properties`() {
        val original = OpenAIResponsesAPIRequest(
            model = "gpt-4o",
            maxOutputTokens = 256,
            additionalProperties = mapOf(
                "custom_extra" to JsonPrimitive("value"),
                "custom_number" to JsonPrimitive(7),
            ),
        )

        val encoded = snakeCaseJson.encodeToString(OpenAIResponsesAPIRequestSerializer, original)
        val decoded = snakeCaseJson.decodeFromString(OpenAIResponsesAPIRequestSerializer, encoded)

        decoded.model shouldBe "gpt-4o"
        decoded.maxOutputTokens shouldBe 256
        decoded.additionalProperties.shouldNotBeNull {
            this["custom_extra"]?.jsonPrimitive?.content shouldBe "value"
            this["custom_number"]?.jsonPrimitive?.content?.toInt() shouldBe 7
        }
    }
}
