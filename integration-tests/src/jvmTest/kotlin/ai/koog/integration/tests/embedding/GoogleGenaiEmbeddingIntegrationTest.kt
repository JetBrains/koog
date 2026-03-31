package ai.koog.integration.tests.embedding

import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestCredentials.readTestGoogleAIKeyFromEnv
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.google.genai.GoogleGenaiEmbeddingProvider
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.google.genai.Client
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

/**
 * Integration tests for [GoogleGenaiEmbeddingProvider].
 */
class GoogleGenaiEmbeddingIntegrationTest {

    companion object {
        @JvmStatic
        fun embeddingModels(): Stream<LLModel> = Stream.of(
            GoogleModels.Embeddings.GeminiEmbedding001,
        )
    }

    private val provider = GoogleGenaiEmbeddingProvider(
        Client.builder()
            .apiKey(readTestGoogleAIKeyFromEnv())
            .vertexAI(false)
            .build()
    )

    @ParameterizedTest
    @MethodSource("embeddingModels")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    fun integration_testEmbed(model: LLModel) = runTest {
        Models.assumeAvailable(model.provider)

        val testText = "integration test embedding"
        provider.embed(testText, model) shouldNotBeNull {
            size shouldBeGreaterThan 100
            shouldForAll {
                it.isFinite()
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun integration_testEmbedRejectsProviderMismatch() = runTest {
        val model = LLModel(
            provider = LLMProvider.Anthropic,
            id = "claude-embed",
            capabilities = listOf(LLMCapability.Embed)
        )
        val error = assertThrows<IllegalArgumentException> { provider.embed("hello", model) }
        error.message shouldContain "provider mismatch"
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun integration_testEmbedRejectsModelWithoutEmbedCapability() = runTest {
        val model = LLModel(
            provider = LLMProvider.Google,
            id = "gemini-2.5-flash",
            capabilities = listOf(LLMCapability.Completion)
        )
        val error = assertThrows<IllegalArgumentException> { provider.embed("hello", model) }
        error.message shouldContain "does not support embed capability"
    }
}
