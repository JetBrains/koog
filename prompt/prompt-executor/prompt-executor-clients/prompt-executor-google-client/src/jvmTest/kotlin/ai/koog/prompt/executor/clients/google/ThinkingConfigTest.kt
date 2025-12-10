package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.executor.clients.google.models.GoogleGenerationConfig
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingLevel
import ai.koog.test.utils.runWithBothJsonConfigurations
import ai.koog.test.utils.verifyDeserialization
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class ThinkingConfigTest {
    @Test
    fun serializeThinkingBudget() {
        val cfg = GoogleGenerationConfig(
            thinkingConfig = GoogleThinkingConfig(thinkingBudget = 0)
        )
        val json = Json.encodeToString(GoogleGenerationConfig.serializer(), cfg)
        assertTrue("\"thinkingBudget\":0" in json)
    }

    @Test fun `test thinkingConfig serialization (Gemini 2 Legacy)`() =
        runWithBothJsonConfigurations("thinkingConfig serialization") { json ->
            val request = GoogleGenerationConfig(
                responseMimeType = "application/json",
                maxOutputTokens = 256,
                temperature = 0.2,
                thinkingConfig = GoogleThinkingConfig(
                    includeThoughts = true,
                    thinkingBudget = 1000,
                )
            )

            val jsonString = json.encodeToString(GoogleGenerationConfig.serializer(), request)

            jsonString shouldEqualJson """
            {
                "responseMimeType": "application/json",
                "maxOutputTokens": 256,
                "temperature": 0.2,
                "thinkingConfig": {
                    "includeThoughts": true,
                    "thinkingBudget": 1000
                }
            }
            """.trimIndent().replace("\r\n", "\n")
        }

    @Test fun `test thinkingConfig serialization (Gemini 3 New)`() =
        runWithBothJsonConfigurations("thinkingConfig serialization gemini 3") { json ->
            val request = GoogleGenerationConfig(
                responseMimeType = "application/json",
                thinkingConfig = GoogleThinkingConfig(
                    includeThoughts = true,
                    thinkingLevel = GoogleThinkingLevel.HIGH
                )
            )

            val jsonString = json.encodeToString(GoogleGenerationConfig.serializer(), request)

            // Verify that thinkingLevel is serialized as "high" and thinkingBudget is absent
            jsonString shouldEqualJson """
            {
                "responseMimeType": "application/json",
                "thinkingConfig": {
                    "includeThoughts": true,
                    "thinkingLevel": "high"
                }
            }
            """.trimIndent().replace("\r\n", "\n")
        }

    @Test fun `test thinkingConfig validation prevents mixing old and new params`() {
        // Should throw IllegalArgumentException because init block checks mutual exclusivity
        shouldThrow<IllegalArgumentException> {
            GoogleThinkingConfig(
                includeThoughts = true,
                thinkingBudget = 1024,
                thinkingLevel = GoogleThinkingLevel.LOW
            )
        }
    }

    @Test
    fun `test thinkingConfig deserialization`() =
        runWithBothJsonConfigurations("thinkingConfig deserialization") { json ->
            val payload = """
            {
              "responseMimeType": "application/json",
              "maxOutputTokens": 256,
              "temperature": 0.2,
              "thinkingConfig": {"includeThoughts": true, "thinkingBudget": 1000}
            }
            """.trimIndent().replace("\r\n", "\n")

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
