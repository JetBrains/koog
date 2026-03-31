package ai.koog.spring.ai.vectorstore

import ai.koog.spring.ai.common.DispatcherConfig
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog Spring AI vector-store adapter.
 *
 * @property enabled whether the auto-configuration is enabled (default `true`)
 * @property vectorStoreBeanName Optional bean name of the [org.springframework.ai.vectorstore.VectorStore]
 *   to use. When set, the named bean is resolved from the application context, allowing selection among multiple
 *   store beans. When not set, the auto-configuration falls back to `@ConditionalOnSingleCandidate`.
 * @property dispatcher Dispatcher / threading settings for blocking Spring AI vector-store calls.
 */
@ConfigurationProperties(prefix = "koog.spring.ai.vectorstore")
public data class KoogSpringAiVectorStoreProperties(
    val enabled: Boolean = true,
    val vectorStoreBeanName: String? = null,
    val dispatcher: DispatcherConfig = DispatcherConfig(),
)
