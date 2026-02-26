package ai.koog.spring.ai.embedding

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog Spring AI Embedding Model adapter.
 *
 * Prefix: `koog.spring-ai.embedding`
 *
 * @property enabled Whether the Koog Spring AI Embedding auto-configuration is enabled. Default: `true`.
 * @property embeddingModelBeanName Optional bean name of the [org.springframework.ai.embedding.EmbeddingModel]
 *   to use when multiple embedding models are registered. When `null`, a single-candidate default is used.
 * @property dispatcher Dispatcher / threading settings for blocking Spring AI model calls.
 */
@ConfigurationProperties(prefix = "koog.spring-ai.embedding")
public data class KoogSpringAIEmbeddingProperties(
    val enabled: Boolean = true,
    val embeddingModelBeanName: String? = null,
    val dispatcher: DispatcherProperties = DispatcherProperties(),
) {
    /**
     * Dispatcher settings for blocking Spring AI model calls.
     *
     * @property type The dispatcher type to use. Default: [DispatcherType.AUTO].
     * @property parallelism Maximum parallelism for the dispatcher (applies to [DispatcherType.FIXED_THREAD_POOL]).
     */
    public data class DispatcherProperties(
        val type: DispatcherType = DispatcherType.AUTO,
        val parallelism: Int = 0,
    )

    /**
     * Dispatcher type for blocking model calls.
     */
    public enum class DispatcherType {
        /**
         * Automatically detect the best dispatcher.
         *
         * When Spring Boot's `spring.threads.virtual.enabled=true` is set, an
         * [org.springframework.core.task.AsyncTaskExecutor] backed by virtual threads
         * is available in the application context. In [AUTO] mode the dispatcher is
         * derived from that executor, so users only need the standard Spring Boot
         * property to opt into virtual threads.
         *
         * Falls back to [kotlinx.coroutines.Dispatchers.IO] when no such executor is present.
         */
        AUTO,

        /** Use [kotlinx.coroutines.Dispatchers.IO]. */
        IO,

        /** Use a fixed-size thread pool with [DispatcherProperties.parallelism] threads. */
        FIXED_THREAD_POOL,
    }
}
