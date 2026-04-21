package ai.koog.spring.ai.memory

import ai.koog.spring.ai.common.DispatcherConfig
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog Spring AI Chat Memory adapter.
 *
 * @property enabled whether the auto-configuration is enabled (default `true`)
 * @property chatMemoryRepositoryBeanName Optional bean name of the [org.springframework.ai.chat.memory.ChatMemoryRepository]
 *   to use. When set, the named bean is resolved from the application context, allowing selection among multiple
 *   repository beans. When not set, the auto-configuration falls back to `@ConditionalOnSingleCandidate`.
 * @property dispatcher Dispatcher / threading settings for blocking Spring AI chat memory repository calls.
 */
@ConfigurationProperties(prefix = "koog.spring.ai.chat-memory")
public data class KoogSpringAiChatMemoryProperties(
    val enabled: Boolean = true,
    val chatMemoryRepositoryBeanName: String? = null,
    val dispatcher: DispatcherConfig = DispatcherConfig(),
)
