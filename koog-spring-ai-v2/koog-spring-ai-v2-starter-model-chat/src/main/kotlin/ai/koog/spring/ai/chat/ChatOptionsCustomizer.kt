package ai.koog.spring.ai.chat

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import org.springframework.ai.chat.prompt.ChatOptions

/**
 * Extension point for provider-specific [org.springframework.ai.chat.prompt.ChatOptions] customization.
 *
 * Implement this interface and register it as a Spring bean to apply
 * provider-specific option tuning on top of the default mapping.
 */
public fun interface ChatOptionsCustomizer {
    /**
     * Customize the given [options] based on the original Koog [params] and [model].
     *
     * @return the customized (or original) [org.springframework.ai.chat.prompt.ChatOptions]
     */
    public fun customize(options: ChatOptions, params: LLMParams, model: LLModel): ChatOptions

    /**
     * A companion object for ChatOptionsCustomizer
     */
    public companion object {
        /** No-op customizer that returns options unchanged. */
        @JvmField
        public val NOOP: ChatOptionsCustomizer = ChatOptionsCustomizer { options, _, _ -> options }
    }
}
