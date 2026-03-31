package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.LLMProviderAware
import ai.koog.prompt.executor.clients.requireMatchingProvider
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.requireCapability
import com.google.genai.Client
import com.google.genai.types.EmbedContentConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.future.await
import kotlin.jvm.optionals.getOrDefault

/**
 * Implementation of [LLMEmbeddingProvider] for Google's Gemini API using the official Google GenAI Java SDK.
 *
 * This provider handles text embedding requests, delegating API calls to the Google GenAI [Client].
 *
 * @property client The configured Google GenAI SDK client.
 * @property llmProvider The provider used for model validation. Defaults based on [Client.vertexAI].
 * @property embedContentConfig Configuration for embedding requests.
 *   Defaults to [EmbedContentConfig] with default settings.
 */
public open class GoogleGenaiEmbeddingProvider @JvmOverloads constructor(
    private val client: Client,
    private val llmProvider: LLMProvider = if (client.vertexAI()) LLMProvider.Vertex else LLMProvider.Google,
    private val embedContentConfig: EmbedContentConfig = EmbedContentConfig.builder().build(),
) : LLMEmbeddingProvider, LLMProviderAware {

    private val logger = KotlinLogging.logger { }

    override fun llmProvider(): LLMProvider = llmProvider

    @OptIn(InternalLLMClientApi::class)
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        requireMatchingProvider(model)
        model.requireCapability(LLMCapability.Embed)

        logger.debug { "Embedding text with model: ${model.id}" }

        val name = this::class.simpleName ?: "GoogleGenaiEmbeddingProvider"
        val response = callGoogleGenaiApi(name) {
            client.async.models.embedContent(model.id, text, embedContentConfig).await()
        }

        return response.embeddings().getOrDefault(emptyList())
            .firstOrNull()
            ?.values()?.getOrDefault(emptyList())
            ?.map { it.toDouble() }
            ?: emptyList()
    }
}
