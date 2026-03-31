package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.google.genai.types.EmbedContentConfig
import com.google.genai.types.EmbedContentResponse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ExecutorService

class GoogleGenaiEmbeddingTest {

    private val mockPair = mockGoogleGenaiClient()
    private val delegate = mockPair.first
    private val asyncModels = mockPair.second
    private val subject = GoogleGenaiEmbeddingProvider(delegate)

    // region embed() response extraction

    @Test
    fun `embed returns empty list for empty embeddings response`() = runTest {
        val emptyResponse = EmbedContentResponse.builder().embeddings(emptyList()).build()
        asyncModels.stubEmbedContent(emptyResponse)

        val result = subject.embed("hello", TestModels.embed)

        result.shouldBeEmpty()
    }

    @Test
    fun `embed preserves all values`() = runTest {
        val floats = (1..768).map { it.toFloat() / 1000f }
        asyncModels.stubEmbedContent(embedResponse(floats))

        val result = subject.embed("hello", TestModels.embed)

        result shouldHaveSize 768
        result.forEachIndexed { index, d ->
            d shouldBe floats[index].toDouble()
        }
    }

    // endregion

    // region Capability validation

    @Test
    fun `embed rejects model without Embed capability`() = runTest {
        val error = assertThrows<IllegalArgumentException> { subject.embed("hello", TestModels.noEmbed) }
        error.message shouldContain "does not support embed capability"
    }

    @Test
    fun `embed rejects model with mismatched provider`() = runTest {
        val model =
            LLModel(provider = LLMProvider.Anthropic, id = "claude-embed", capabilities = listOf(LLMCapability.Embed))
        val error = assertThrows<IllegalArgumentException> { subject.embed("hello", model) }
        error.message shouldContain "provider mismatch"
    }

    // endregion

    // region EmbedContentConfig passthrough

    @Test
    fun `embed passes provided EmbedContentConfig to the API`() = runTest {
        val customConfig = EmbedContentConfig.builder().outputDimensionality(256).build()
        val providerWithConfig = GoogleGenaiEmbeddingProvider(delegate, embedContentConfig = customConfig)
        val captured = asyncModels.stubEmbedContent(embedResponse(0.1f))

        providerWithConfig.embed("hello", TestModels.embed)

        captured.config shouldBe customConfig
        captured.config.outputDimensionality().get() shouldBe 256
    }

    // endregion
}
