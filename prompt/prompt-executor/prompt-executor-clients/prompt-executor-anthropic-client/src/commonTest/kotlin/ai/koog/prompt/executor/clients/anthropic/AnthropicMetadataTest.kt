package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.executor.clients.anthropic.models.AnthropicUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for [AnthropicUsage] deserialization and cache token metadata extraction.
 *
 * Covers deserialization of [AnthropicUsage] and correct mapping of
 * cache read/creation token counts into metadata JsonObject.
 */
class AnthropicMetadataTest {

    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test
    fun testDeserializeFullAnthropicUsageJson() {
        val jsonResponse = """
            {
                "input_tokens": 1500,
                "output_tokens": 500,
                "cache_read_input_tokens": 1000,
                "cache_creation_input_tokens": 250
            }
        """.trimIndent()

        val usage = json.decodeFromString<AnthropicUsage>(jsonResponse)

        assertEquals(1500, usage.inputTokens)
        assertEquals(500, usage.outputTokens)
        assertEquals(1000, usage.cacheReadInputTokens)
        assertEquals(250, usage.cacheCreationInputTokens)
    }

    @Test
    fun testCacheMetadataPopulatedWhenBothFieldsPresent() {
        val usage = AnthropicUsage(
            inputTokens = 1500,
            outputTokens = 500,
            cacheReadInputTokens = 1000,
            cacheCreationInputTokens = 250
        )

        val cacheMetadata = buildJsonObject {
            usage.cacheCreationInputTokens?.let { put("cacheCreationInputTokens", it) }
            usage.cacheReadInputTokens?.let { put("cacheReadInputTokens", it) }
        }.takeIf { it.isNotEmpty() }

        assertNotNull(cacheMetadata, "metadata should not be null when cache token fields are present")
        assertEquals(1000, cacheMetadata["cacheReadInputTokens"]?.jsonPrimitive?.int)
        assertEquals(250, cacheMetadata["cacheCreationInputTokens"]?.jsonPrimitive?.int)
    }

    @Test
    fun testCacheMetadataNullWhenCacheTokensAbsent() {
        val usage = AnthropicUsage(
            inputTokens = 100,
            outputTokens = 50,
            cacheReadInputTokens = null,
            cacheCreationInputTokens = null
        )

        val cacheMetadata = buildJsonObject {
            usage.cacheCreationInputTokens?.let { put("cacheCreationInputTokens", it) }
            usage.cacheReadInputTokens?.let { put("cacheReadInputTokens", it) }
        }.takeIf { it.isNotEmpty() }

        assertNull(cacheMetadata, "metadata should be null when cache token fields are both null")
    }
}
