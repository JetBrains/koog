package ai.koog.spring.ai.chat

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog Spring AI Chat Model adapter.
 *
 * Prefix: `koog.spring-ai.chat`
 *
 * @property enabled Whether the Koog Spring AI Chat auto-configuration is enabled. Default: `true`.
 * @property chatModelBeanName Optional bean name of the [org.springframework.ai.chat.model.ChatModel]
 *   to use when multiple chat models are registered. When `null`, a single-candidate default is used.
 * @property moderationModelBeanName Optional bean name of the [org.springframework.ai.moderation.ModerationModel]
 *   to use when multiple moderation models are registered. When `null`, the single candidate (if any) is used;
 *   with multiple candidates the injection is skipped to avoid [org.springframework.beans.factory.NoUniqueBeanDefinitionException].
 * @property dispatcher Dispatcher / threading settings for blocking Spring AI model calls.
 */
@ConfigurationProperties(prefix = "koog.spring-ai.chat")
public data class KoogSpringAIChatProperties(
    val enabled: Boolean = true,
    val chatModelBeanName: String? = null,
    val moderationModelBeanName: String? = null,
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
