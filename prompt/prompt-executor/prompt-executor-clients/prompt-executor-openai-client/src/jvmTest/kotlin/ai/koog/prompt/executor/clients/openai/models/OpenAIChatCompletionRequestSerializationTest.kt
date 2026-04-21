package ai.koog.prompt.executor.clients.openai.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.test.utils.runWithBothJsonConfigurations
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

class OpenAIChatCompletionRequestSerializationTest {

    @Test
    fun `test serialization without additionalProperties`() =
        runWithBothJsonConfigurations("serialization without additionalProperties") { json ->
            json.encodeToString(
                OpenAIChatCompletionRequestSerializer,
                OpenAIChatCompletionRequest(
                    model = "gpt-4",
                    messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
                    temperature = 0.7,
                    maxTokens = 1000
                )
            ) shouldEqualJson """
            {
                "model": "gpt-4",
                "messages": [{"role": "user", "content": "Hello"}],
                "temperature": 0.7,
                "maxTokens": 1000
            }
            """.trimIndent()
        }

    @Test
    fun `test serialization with additionalProperties`() =
        runWithBothJsonConfigurations("serialization with additionalProperties") { json ->
            json.encodeToString(
                OpenAIChatCompletionRequestSerializer,
                OpenAIChatCompletionRequest(
                    model = "gpt-4",
                    messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
                    temperature = 0.7,
                    additionalProperties = mapOf<String, JsonElement>(
                        "customProperty" to JsonPrimitive("customValue"),
                        "customNumber" to JsonPrimitive(42),
                        "customBoolean" to JsonPrimitive(true)
                    )
                )
            ) shouldEqualJson """
            {
                "model": "gpt-4",
                "messages": [{"role": "user", "content": "Hello"}],
                "temperature": 0.7,
                "customProperty": "customValue",
                "customNumber": 42,
                "customBoolean": true
            }
            """.trimIndent()
        }

    @Test
    fun `test deserialization without additional properties`() =
        runWithBothJsonConfigurations("deserialization without additional properties") { json ->
            val jsonInput = buildJsonObject {
                put("model", JsonPrimitive("gpt-4"))
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
            }

            json.decodeFromJsonElement(OpenAIChatCompletionRequestSerializer, jsonInput).shouldNotBeNull {
                model shouldBe "gpt-4"
                temperature shouldBe 0.7
                maxTokens shouldBe 1000
                additionalProperties.shouldBeNull()
            }
        }

    @Test
    fun `test deserialization with additional properties`() =
        runWithBothJsonConfigurations("deserialization with additional properties") { json ->
            val jsonInput = buildJsonObject {
                put("model", JsonPrimitive("gpt-4"))
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

            json.decodeFromJsonElement(OpenAIChatCompletionRequestSerializer, jsonInput).shouldNotBeNull {
                model shouldBe "gpt-4"
                temperature shouldBe 0.7
                additionalProperties.shouldNotBeNull {
                    size shouldBe 3
                    this["customProperty"]?.jsonPrimitive?.contentOrNull shouldBe "customValue"
                    this["customNumber"]?.jsonPrimitive?.intOrNull shouldBe 42
                    this["customBoolean"]?.jsonPrimitive?.booleanOrNull shouldBe true
                }
            }
        }

    @Test
    fun `test round trip serialization with additionalProperties`() =
        runWithBothJsonConfigurations("round trip serialization with additionalProperties") { json ->
            val originalAdditionalProperties = mapOf<String, JsonElement>(
                "customProperty" to JsonPrimitive("customValue"),
                "customNumber" to JsonPrimitive(42)
            )

            val originalRequest = OpenAIChatCompletionRequest(
                model = "gpt-4",
                messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
                temperature = 0.7,
                additionalProperties = originalAdditionalProperties
            )

            json.decodeFromString(
                OpenAIChatCompletionRequestSerializer,
                json.encodeToString(OpenAIChatCompletionRequestSerializer, originalRequest)
            ).shouldNotBeNull {
                model shouldBe originalRequest.model
                temperature shouldBe originalRequest.temperature
                messages shouldHaveSize originalRequest.messages.size
                additionalProperties.shouldNotBeNull {
                    size shouldBe originalAdditionalProperties.size
                    this["customProperty"]?.jsonPrimitive?.contentOrNull shouldBe "customValue"
                    this["customNumber"]?.jsonPrimitive?.intOrNull shouldBe 42
                }
            }
        }
}
