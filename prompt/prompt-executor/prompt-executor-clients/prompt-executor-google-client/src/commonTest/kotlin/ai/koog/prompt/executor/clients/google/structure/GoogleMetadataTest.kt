package ai.koog.prompt.executor.clients.google.structure

import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.models.GoogleUsageMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for [GoogleLLMClient.createMetaInfo] token metadata extraction.
 *
 * Covers deserialization of [GoogleUsageMetadata] and correct mapping of
 * cached/reasoning token counts into [ResponseMetaInfo.metadata].
 */
class GoogleMetadataTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = GoogleLLMClient(
        apiKey = "test-key",
        settings = GoogleClientSettings()
    )

    @Test
    fun testDeserializeFullGoogleUsageJson() {
        val jsonResponse = """
            {
                "promptTokenCount": 120,
                "candidatesTokenCount": 45,
                "totalTokenCount": 165,
                "cachedContentTokenCount": 100,
                "thoughtsTokenCount": 25
            }
        """.trimIndent()

        val usage = json.decodeFromString<GoogleUsageMetadata>(jsonResponse)

        assertEquals(120, usage.promptTokenCount)
        assertEquals(45, usage.candidatesTokenCount)
        assertEquals(165, usage.totalTokenCount)
        assertEquals(100, usage.cachedContentTokenCount)
        assertEquals(25, usage.thoughtsTokenCount)
    }

    @Test
    fun testCreateMetaInfoMapsAllFieldsCorrectly() {
        val usage = GoogleUsageMetadata(
            promptTokenCount = 120,
            candidatesTokenCount = 45,
            totalTokenCount = 165,
            cachedContentTokenCount = 100,
            thoughtsTokenCount = 25
        )

        val metaInfo = client.createMetaInfo(usage)

        assertEquals(165, metaInfo.totalTokensCount)
        assertEquals(120, metaInfo.inputTokensCount)
        assertEquals(45, metaInfo.outputTokensCount)

        val metadata = metaInfo.metadata
        assertNotNull(metadata, "metadata should not be null when cachedContentTokenCount or thoughtsTokenCount is present")
        assertEquals(100, metadata["cachedTokens"]?.jsonPrimitive?.int)
        assertEquals(25, metadata["reasoningTokens"]?.jsonPrimitive?.int)
    }

    @Test
    fun testCreateMetaInfoNullCacheAndThoughtsReturnsNullMetadata() {
        val usage = GoogleUsageMetadata(
            promptTokenCount = 50,
            candidatesTokenCount = 10,
            totalTokenCount = 60,
            cachedContentTokenCount = null,
            thoughtsTokenCount = null
        )

        val metaInfo = client.createMetaInfo(usage)

        assertEquals(60, metaInfo.totalTokensCount)
        assertNull(metaInfo.metadata, "metadata should be null when cachedContentTokenCount and thoughtsTokenCount are both null")
    }
}
