package ai.koog.prompt.executor.clients.openai.base

import ai.koog.prompt.executor.clients.openai.base.models.CompletionTokensDetails
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import ai.koog.prompt.executor.clients.openai.base.models.PromptTokensDetails
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for token metadata extraction in [AbstractOpenAILLMClient.createMetaInfo].
 *
 * Verifies that cached token counts and reasoning token counts are correctly
 * propagated from [OpenAIUsage] into [ResponseMetaInfo.metadata].
 */
class OpenAITokenMetadataTest {

    private val client = OpenAITokenMetadataTestHelper()

    @Test
    fun testCreateMetaInfoNullUsageReturnsNullMetadata() {
        val meta = client.createMetaInfoForTest(null)
        assertNull(meta.metadata, "metadata should be null when usage is null")
    }

    @Test
    fun testCreateMetaInfoNoDetailFieldsReturnsNullMetadata() {
        val usage = OpenAIUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15)
        val meta = client.createMetaInfoForTest(usage)
        assertNull(meta.metadata, "metadata should be null when promptTokensDetails and completionTokensDetails are absent")
    }

    @Test
    fun testCreateMetaInfoAllNullDetailValuesReturnsNullMetadata() {
        val usage = OpenAIUsage(
            promptTokens = 10,
            completionTokens = 5,
            totalTokens = 15,
            promptTokensDetails = PromptTokensDetails(cachedTokens = null, audioTokens = null),
            completionTokensDetails = CompletionTokensDetails(reasoningTokens = null)
        )
        val meta = client.createMetaInfoForTest(usage)
        assertNull(meta.metadata, "metadata should be null when all detail fields are null")
    }

    @Test
    fun testCreateMetaInfoPopulatesCachedTokens() {
        val usage = OpenAIUsage(
            promptTokens = 100,
            completionTokens = 50,
            totalTokens = 150,
            promptTokensDetails = PromptTokensDetails(cachedTokens = 42)
        )
        val meta = client.createMetaInfoForTest(usage)

        assertNotNull(meta.metadata)
        assertEquals(42, meta.metadata!!["cachedTokens"]?.jsonPrimitive?.int)
        assertNull(meta.metadata!!["reasoningTokens"], "reasoningTokens should be absent when only cachedTokens is provided")
    }

    @Test
    fun testCreateMetaInfoCachedTokensZeroIsIncluded() {
        val usage = OpenAIUsage(
            promptTokens = 10,
            completionTokens = 5,
            totalTokens = 15,
            promptTokensDetails = PromptTokensDetails(cachedTokens = 0)
        )
        val meta = client.createMetaInfoForTest(usage)

        assertNotNull(meta.metadata, "metadata should not be null even when cachedTokens is 0")
        assertEquals(0, meta.metadata!!["cachedTokens"]?.jsonPrimitive?.int)
    }

    @Test
    fun testCreateMetaInfoPopulatesReasoningTokens() {
        val usage = OpenAIUsage(
            promptTokens = 100,
            completionTokens = 200,
            totalTokens = 300,
            completionTokensDetails = CompletionTokensDetails(reasoningTokens = 75)
        )
        val meta = client.createMetaInfoForTest(usage)

        assertNotNull(meta.metadata)
        assertEquals(75, meta.metadata!!["reasoningTokens"]?.jsonPrimitive?.int)
        assertNull(meta.metadata!!["cachedTokens"], "cachedTokens should be absent when only reasoningTokens is provided")
    }

    @Test
    fun testCreateMetaInfoReasoningTokensZeroIsIncluded() {
        val usage = OpenAIUsage(
            promptTokens = 10,
            completionTokens = 5,
            totalTokens = 15,
            completionTokensDetails = CompletionTokensDetails(reasoningTokens = 0)
        )
        val meta = client.createMetaInfoForTest(usage)

        assertNotNull(meta.metadata)
        assertEquals(0, meta.metadata!!["reasoningTokens"]?.jsonPrimitive?.int)
    }

    @Test
    fun testCreateMetaInfoPopulatesBothCachedAndReasoningTokens() {
        val usage = OpenAIUsage(
            promptTokens = 100,
            completionTokens = 200,
            totalTokens = 300,
            promptTokensDetails = PromptTokensDetails(cachedTokens = 40),
            completionTokensDetails = CompletionTokensDetails(reasoningTokens = 80)
        )
        val meta = client.createMetaInfoForTest(usage)

        assertNotNull(meta.metadata)
        assertEquals(40, meta.metadata!!["cachedTokens"]?.jsonPrimitive?.int)
        assertEquals(80, meta.metadata!!["reasoningTokens"]?.jsonPrimitive?.int)
    }

    @Test
    fun testCreateMetaInfoStandardTokenCountsAreCorrect() {
        val usage = OpenAIUsage(
            promptTokens = 100,
            completionTokens = 200,
            totalTokens = 300,
            promptTokensDetails = PromptTokensDetails(cachedTokens = 50),
            completionTokensDetails = CompletionTokensDetails(reasoningTokens = 30)
        )
        val meta = client.createMetaInfoForTest(usage)

        assertEquals(100, meta.inputTokensCount)
        assertEquals(200, meta.outputTokensCount)
        assertEquals(300, meta.totalTokensCount)
    }

    @Test
    fun testCreateMetaInfoDoesNotExposeAudioTokens() {
        val usage = OpenAIUsage(
            promptTokens = 100,
            completionTokens = 50,
            totalTokens = 150,
            promptTokensDetails = PromptTokensDetails(cachedTokens = 30, audioTokens = 10)
        )
        val meta = client.createMetaInfoForTest(usage)

        assertNotNull(meta.metadata)
        assertNull(meta.metadata!!["audioTokens"], "audioTokens should not be exposed in metadata")
        assertNotNull(meta.metadata!!["cachedTokens"])
    }

    @Test
    fun testCreateMetaInfoDoesNotExposeAcceptedPredictionTokens() {
        val usage = OpenAIUsage(
            promptTokens = 100,
            completionTokens = 50,
            totalTokens = 150,
            completionTokensDetails = CompletionTokensDetails(
                reasoningTokens = 20,
                acceptedPredictionTokens = 5
            )
        )
        val meta = client.createMetaInfoForTest(usage)

        assertNotNull(meta.metadata)
        assertNull(meta.metadata!!["acceptedPredictionTokens"], "acceptedPredictionTokens should not be exposed in metadata")
        assertNotNull(meta.metadata!!["reasoningTokens"])
    }
}
