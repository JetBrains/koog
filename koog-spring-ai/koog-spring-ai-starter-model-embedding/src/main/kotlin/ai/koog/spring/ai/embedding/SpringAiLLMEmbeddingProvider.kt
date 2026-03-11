package ai.koog.spring.ai.embedding

import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingOptions
import org.springframework.ai.embedding.EmbeddingRequest

/**
 * An [LLMEmbeddingProvider] implementation that delegates to a Spring AI [EmbeddingModel].
 *
 * When multiple embedding models are registered in the Spring context, use the
 * `koog.spring.ai.embedding.embedding-model-bean-name` property to select the desired bean.
 * The [LLModel.id] is forwarded to the underlying Spring AI model via [EmbeddingOptions] so
 * that backends which support runtime model selection (e.g. OpenAI-compatible endpoints) can
 * honour it; backends that ignore the option will simply use their pre-configured model.
 *
 * @param embeddingModel the Spring AI embedding model to delegate to
 * @param dispatcher the [CoroutineDispatcher] used for blocking embedding calls
 */
public class SpringAiLLMEmbeddingProvider(
    private val embeddingModel: EmbeddingModel,
    private val dispatcher: CoroutineDispatcher,
) : LLMEmbeddingProvider {

    /**
     * Java-friendly builder access.
     */
    public companion object {
        /**
         * Returns a new [Builder] for constructing a [SpringAiLLMEmbeddingProvider].
         * Intended for Java callers who want to avoid dealing with Kotlin default parameters.
         *
         * Usage:
         * ```java
         * SpringAiLLMEmbeddingProvider.builder()
         *     .embeddingModel(embeddingModel)
         *     .dispatcher(dispatcher)
         *     .build();
         * ```
         */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }

    /**
     * A Java-friendly builder for [SpringAiLLMEmbeddingProvider].
     *
     * The only required property is [embeddingModel]; all others have sensible defaults.
     */
    public class Builder {
        private var embeddingModel: EmbeddingModel? = null
        private var dispatcher: CoroutineDispatcher = Dispatchers.IO

        /** Sets the Spring AI [EmbeddingModel] to delegate to. Required. */
        public fun embeddingModel(embeddingModel: EmbeddingModel): Builder =
            apply { this.embeddingModel = embeddingModel }

        /** Sets the [CoroutineDispatcher] used for blocking embedding calls. Default is [Dispatchers.IO]. */
        public fun dispatcher(dispatcher: CoroutineDispatcher): Builder = apply { this.dispatcher = dispatcher }

        /**
         * Builds a new [SpringAiLLMEmbeddingProvider] instance.
         *
         * @throws IllegalStateException if [embeddingModel] has not been set
         */
        public fun build(): SpringAiLLMEmbeddingProvider {
            val embeddingModel = requireNotNull(this.embeddingModel) { "embeddingModel must be set" }
            return SpringAiLLMEmbeddingProvider(
                embeddingModel = embeddingModel,
                dispatcher = dispatcher,
            )
        }
    }

    override suspend fun embed(
        text: String,
        model: LLModel
    ): List<Double> = withContext(dispatcher) {
        val request = EmbeddingRequest(
            listOf(text),
            EmbeddingOptions.builder()
                .model(model.id)
                .build()
        )
        try {
            embeddingModel.call(request).result.output.map { it.toDouble() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException("spring-ai-embedding", "EmbeddingModel.call() failed: ${e.message}", e)
        }
    }
}
