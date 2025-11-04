package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.executor.clients.google.models.GoogleGenerationConfig
import ai.koog.prompt.executor.clients.google.models.GoogleRequest
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.test.utils.runWithBothJsonConfigurations
import ai.koog.test.utils.verifyDeserialization
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class GoogleSerializationTest {

    @Test
    fun `test serialization without additionalProperties`() =
        runWithBothJsonConfigurations("serialization without additionalProperties") { json ->
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

            jsonObject["responseMimeType"]?.jsonPrimitive?.contentOrNull shouldBe "application/json"
            jsonObject["maxOutputTokens"]?.jsonPrimitive?.intOrNull shouldBe 1000
            jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull shouldBe 0.7
            jsonObject["candidateCount"]?.jsonPrimitive?.intOrNull shouldBe 1
            jsonObject["topP"]?.jsonPrimitive?.doubleOrNull shouldBe 0.9
            jsonObject["topK"]?.jsonPrimitive?.intOrNull shouldBe 40
            jsonObject["additionalProperties"] shouldBe null
        }

    @Test
    fun `test serialization with additionalProperties`() =
        runWithBothJsonConfigurations("test serialization with additionalProperties") { json ->
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
            jsonObject["responseMimeType"]?.jsonPrimitive?.contentOrNull
            jsonObject["maxOutputTokens"]?.jsonPrimitive?.intOrNull
            jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull

            // Additional properties should be flattened to root level
            jsonObject["customProperty"]?.jsonPrimitive?.contentOrNull shouldBe "customValue"
            jsonObject["customNumber"]?.jsonPrimitive?.intOrNull shouldBe 42
            jsonObject["customBoolean"]?.jsonPrimitive?.booleanOrNull shouldBe true

            // additionalProperties field itself should not be present in serialized JSON
            jsonObject["additionalProperties"] shouldBe null
        }

    @Test
    fun `test deserialization without additional properties`() =
        runWithBothJsonConfigurations("test deserialization without additionalProperties") { json ->
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

            request.responseMimeType shouldBe "application/json"
            request.maxOutputTokens shouldBe 1000
            request.temperature shouldBe 0.7
            request.candidateCount shouldBe 1
            request.topP shouldBe 0.9
            request.topK shouldBe 40
            request.additionalProperties shouldBe null
        }

    @Test
    fun `test deserialization with additional properties`() =
        runWithBothJsonConfigurations("test deserialization with additionalProperties") { json ->
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

            generationConfig.responseMimeType shouldBe "application/json"
            generationConfig.maxOutputTokens shouldBe 1000
            generationConfig.temperature shouldBe 0.7

            val additionalProps = generationConfig.additionalProperties
            additionalProps shouldNotBe null
            additionalProps!!.size shouldBe 3
            additionalProps["customProperty"]?.jsonPrimitive?.contentOrNull shouldBe "customValue"
            additionalProps["customNumber"]?.jsonPrimitive?.intOrNull shouldBe 42
            additionalProps["customBoolean"]?.jsonPrimitive?.booleanOrNull shouldBe true
        }

    @Test
    fun `test thinkingConfig serialization`() =
        runWithBothJsonConfigurations("thinkingConfig serialization") { json ->
            val request = GoogleGenerationConfig(
                responseMimeType = "application/json",
                maxOutputTokens = 256,
                temperature = 0.2,
                thinkingConfig = GoogleThinkingConfig(
                    includeThoughts = true,
                    thinkingBudget = 1000
                )
            )

            val jsonString = json.encodeToString(GoogleGenerationConfig.serializer(), request)

            jsonString shouldEqualJson
                // language=json
                """
            {
                "responseMimeType": "application/json",
                "maxOutputTokens": 256,
                "temperature": 0.2,
                "thinkingConfig": {
                    "includeThoughts": true,
                    "thinkingBudget": 1000
                }
            }
                """.trimIndent()
        }

    @Test
    fun `test thinkingConfig deserialization`() =
        runWithBothJsonConfigurations("thinkingConfig deserialization") { json ->
            val payload =
                // language=json
                """
            {
              "responseMimeType": "application/json",
              "maxOutputTokens": 256,
              "temperature": 0.2,
              "thinkingConfig": {"includeThoughts": true, "thinkingBudget": 1000}
            }
                """.trimIndent()

            val decoded: GoogleGenerationConfig = verifyDeserialization(
                payload = payload,
                json = json
            )

            decoded.responseMimeType shouldBe "application/json"
            decoded.maxOutputTokens shouldBe 256
            decoded.temperature shouldBe 0.2
            decoded.thinkingConfig shouldNotBe null
            decoded.thinkingConfig?.includeThoughts shouldBe true
            decoded.thinkingConfig?.thinkingBudget shouldBe 1000
        }
}
