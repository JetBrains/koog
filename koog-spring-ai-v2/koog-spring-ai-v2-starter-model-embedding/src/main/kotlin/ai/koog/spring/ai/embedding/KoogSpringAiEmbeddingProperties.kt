package ai.koog.spring.ai.embedding

import ai.koog.spring.ai.common.DispatcherConfig
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
    val dispatcher: DispatcherConfig = DispatcherConfig(),
)
