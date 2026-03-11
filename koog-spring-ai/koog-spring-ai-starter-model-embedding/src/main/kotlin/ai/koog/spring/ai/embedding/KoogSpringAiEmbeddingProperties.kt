package ai.koog.spring.ai.embedding

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog Spring AI Embedding Model adapter.
 *
 * Prefix: `koog.spring.ai.embedding`
 *
 * @property enabled Whether the Koog Spring AI Embedding auto-configuration is enabled. Default: `true`.
 * @property embeddingModelBeanName Optional bean name of the [org.springframework.ai.embedding.EmbeddingModel]
 *   to use when multiple embedding models are registered. When `null`, a single-candidate default is used.
 * @property dispatcher Dispatcher / threading settings for blocking Spring AI model calls.
 */
@ConfigurationProperties(prefix = "koog.spring.ai.embedding")
public data class KoogSpringAiEmbeddingProperties(
    val enabled: Boolean = true,
    val embeddingModelBeanName: String? = null,
    val dispatcher: DispatcherProperties = DispatcherProperties(),
) {
    /**
     * Dispatcher settings for blocking Spring AI model calls.
     *
     * @property type The dispatcher type to use. Default: [DispatcherType.AUTO].
     * @property parallelism Maximum parallelism for the dispatcher. When greater than 0 and [DispatcherType.IO]
     *   is selected, `Dispatchers.IO.limitedParallelism(parallelism)` is used instead of the unbounded `Dispatchers.IO`.
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
         *
         * **Warning:** When `spring.threads.virtual.enabled=false` (the default before
         * Spring Boot 3.2), the application task executor is typically a bounded
         * `ThreadPoolTaskExecutor` (8 core threads by default). Wrapping it as a
         * coroutine dispatcher means all blocking model calls share the same thread pool
         * used by `@Async`, scheduled tasks, and web MVC async handlers. Under load this
         * can cause thread starvation or deadlocks. In such setups, prefer [IO] or enable
         * virtual threads.
         */
        AUTO,

        /**
         * Use [kotlinx.coroutines.Dispatchers.IO].
         *
         * When [DispatcherProperties.parallelism] is greater than 0, uses
         * `Dispatchers.IO.limitedParallelism(parallelism)` to cap concurrency.
         */
        IO,
    }
}
