package ai.koog.spring.ai.chat

import ai.koog.spring.ai.common.DispatcherConfig
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog Spring AI Chat Model adapter.
 *
 * Prefix: `koog.spring.ai.chat`
 *
 * @property enabled Whether the Koog Spring AI Chat auto-configuration is enabled. Default: `true`.
 * @property chatModelBeanName Optional bean name of the [org.springframework.ai.chat.model.ChatModel]
 *   to use when multiple chat models are registered. When `null`, a single-candidate default is used.
 * @property moderationModelBeanName Optional bean name of the [org.springframework.ai.moderation.ModerationModel]
 *   to use when multiple moderation models are registered. When `null`, the single candidate (if any) is used;
 *   with multiple candidates the injection is skipped to avoid [org.springframework.beans.factory.NoUniqueBeanDefinitionException].
 * @property provider Optional LLM provider identifier (e.g. `google`, `openai`, `anthropic`).
 *   When set, the [ai.koog.prompt.llm.LLMProvider] passed to [SpringAiLLMClient] is resolved from
 *   the well-known Koog providers by this id. When `null` (default), the provider is auto-detected
 *   from the [org.springframework.ai.chat.model.ChatModel] implementation class name.
 *   If auto-detection fails, a generic `spring-ai` provider is used as a fallback.
 * @property dispatcher Dispatcher / threading settings for blocking Spring AI model calls.
 */
@ConfigurationProperties(prefix = "koog.spring.ai.chat")
public data class KoogSpringAiChatProperties(
    val enabled: Boolean = true,
    val chatModelBeanName: String? = null,
    val moderationModelBeanName: String? = null,
    val provider: String? = null,
    val dispatcher: DispatcherConfig = DispatcherConfig(),
)
