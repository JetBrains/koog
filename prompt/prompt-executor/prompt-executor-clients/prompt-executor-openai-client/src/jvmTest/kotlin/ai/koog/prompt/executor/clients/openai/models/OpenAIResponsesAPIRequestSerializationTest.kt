package ai.koog.prompt.executor.clients.openai.models

import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.base.models.ServiceTier
import ai.koog.test.utils.runWithBothJsonConfigurations
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class OpenAIResponsesAPIRequestSerializationTest {
    @Test
    fun `test serialization without additionalProperties`() =
        runWithBothJsonConfigurations("serialization without additionalProperties") { json ->
            val request = OpenAIResponsesAPIRequest(
                model = "gpt-4o",
                instructions = "Please help with this task",
                temperature = 0.7,
                maxOutputTokens = 1000,
                stream = false
            )

            val jsonString = json.encodeToString(OpenAIResponsesAPIRequestSerializer, request)

            jsonString shouldEqualJson
                // language=json
                """
            {
                "model": "gpt-4o",
                "instructions": "Please help with this task",
                "temperature": 0.7,
                "maxOutputTokens": 1000,
                "stream": false
            }
                """.trimIndent()
        }

    @Test
    fun `test serialization with additionalProperties`() =
        runWithBothJsonConfigurations("serialization with additionalProperties") { json ->
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

            val jsonString = json.encodeToString(OpenAIResponsesAPIRequestSerializer, request)

            jsonString shouldEqualJson
                // language=json
                """
            {
                "model": "gpt-4o",
                "instructions": "Please help with this task",
                "temperature": 0.7,
                "customProperty": "customValue",
                "customNumber": 42,
                "customBoolean": true
            }
                """.trimIndent()
        }

    @Test
    fun `test deserialization without additional properties`() =
        runWithBothJsonConfigurations("test deserialization without additionalProperties") { json ->
            val jsonInput = buildJsonObject {
                put("model", JsonPrimitive("gpt-4o"))
                put("instructions", JsonPrimitive("Please help with this task"))
                put("temperature", JsonPrimitive(0.7))
                put("maxOutputTokens", JsonPrimitive(1000))
                put("stream", JsonPrimitive(false))
            }

            val request = json.decodeFromJsonElement(OpenAIResponsesAPIRequestSerializer, jsonInput)

            request.model shouldBe "gpt-4o"
            request.instructions shouldBe "Please help with this task"
            request.temperature shouldBe 0.7
            request.maxOutputTokens shouldBe 1000
            request.stream shouldBe false
            request.additionalProperties shouldBe null
        }

    @Test
    fun `test deserialization with additional properties`() =
        runWithBothJsonConfigurations("test deserialization with additionalProperties") { json ->
            val jsonInput = buildJsonObject {
                put("model", JsonPrimitive("gpt-4o"))
                put("instructions", JsonPrimitive("Please help with this task"))
                put("temperature", JsonPrimitive(0.7))
                put("customProperty", JsonPrimitive("customValue"))
                put("customNumber", JsonPrimitive(42))
                put("customBoolean", JsonPrimitive(true))
            }

            val request = json.decodeFromJsonElement(OpenAIResponsesAPIRequestSerializer, jsonInput)

            request.model shouldBe "gpt-4o"
            request.instructions shouldBe "Please help with this task"
            request.temperature shouldBe 0.7

            request.additionalProperties shouldNotBe null
            val additionalProps = request.additionalProperties!!
            additionalProps.size shouldBe 3
            additionalProps["customProperty"]?.jsonPrimitive?.contentOrNull shouldBe "customValue"
            additionalProps["customNumber"]?.jsonPrimitive?.intOrNull shouldBe 42
            additionalProps["customBoolean"]?.jsonPrimitive?.booleanOrNull shouldBe true
        }

    @Test
    fun `test full serialization of OpenAIResponsesAPIRequest fields`() =
        runWithBothJsonConfigurations("test full serialization of OpenAIResponsesAPIRequest fields") { json ->
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

            val jsonString = json.encodeToString(OpenAIResponsesAPIRequestSerializer, request)

            jsonString shouldEqualJson
                // language=json
                """
            {
                "model": "gpt-4o",
                "instructions": "sys-msg",
                "temperature": 0.5,
                "maxOutputTokens": 321,
                "stream": false,
                "background": true,
                "include": ["message.output_text.logprobs", "reasoning.encrypted_content"],
                "maxToolCalls": 7,
                "parallelToolCalls": true,
                "reasoning": {
                    "effort": "high",
                    "summary": "concise"
                },
                "truncation": "auto",
                "promptCacheKey": "pck",
                "safetyIdentifier": "sid",
                "serviceTier": "flex",
                "store": true,
                "topLogprobs": 5,
                "topP": 0.9,
                "user": "user-123",
                "extra": "value"
            }
                """.trimIndent()
        }

    @Test
    fun `test full deserialization of OpenAIResponsesAPIRequest fields`() =
        runWithBothJsonConfigurations("test full deserialization of OpenAIResponsesAPIRequest fields") { json ->
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

            decoded.model shouldBe "gpt-4o"
            decoded.instructions shouldBe "sys-msg"
            decoded.temperature shouldBe 0.5
            decoded.maxOutputTokens shouldBe 321
            decoded.stream shouldBe false
            decoded.background shouldBe true
            decoded.include shouldBe listOf(
                OpenAIInclude.OUTPUT_TEXT_LOGPROBS,
                OpenAIInclude.REASONING_ENCRYPTED_CONTENT
            )
            decoded.maxToolCalls shouldBe 7
            decoded.parallelToolCalls shouldBe true
            decoded.truncation shouldBe Truncation.AUTO
            decoded.promptCacheKey shouldBe "pck"
            decoded.safetyIdentifier shouldBe "sid"
            decoded.serviceTier shouldBe ServiceTier.FLEX
            decoded.store shouldBe true
            decoded.topLogprobs shouldBe 5
            decoded.topP shouldBe 0.9
            decoded.user shouldBe "user-123"

            decoded.reasoning shouldNotBe null
            decoded.reasoning?.effort shouldBe ReasoningEffort.HIGH
            decoded.reasoning?.summary shouldBe ReasoningSummary.CONCISE

            decoded.additionalProperties shouldNotBe null
            decoded.additionalProperties?.get("extra")?.jsonPrimitive?.content shouldBe "value"
            decoded.additionalProperties?.get("customNumber")?.jsonPrimitive?.intOrNull shouldBe 42
        }
}
