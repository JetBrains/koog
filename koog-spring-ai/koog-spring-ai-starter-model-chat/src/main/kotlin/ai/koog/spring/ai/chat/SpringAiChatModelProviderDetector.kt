package ai.koog.spring.ai.chat

import ai.koog.prompt.llm.LLMProvider
import org.springframework.ai.chat.model.ChatModel

/**
 * Resolves the [LLMProvider] to use for a [SpringAiLLMClient].
 *
 * Resolution order:
 * 1. Explicit property value (`koog.spring.ai.chat.provider`) looked up by [LLMProvider.id].
 * 2. Auto-detection from the [ChatModel] implementation class name.
 * 3. Fallback to [SpringAiLLMProvider].
 */
internal object SpringAiChatModelProviderDetector {

    /**
     * Well-known Koog providers indexed by their [LLMProvider.id].
     *
     * Sorted by descending id length so that longer (more specific) ids are matched first
     * during auto-detection (e.g. `mistralai` before `meta`).
     */
    private val knownProviders: List<LLMProvider> = listOf(
        LLMProvider.Google,
        LLMProvider.OpenAI,
        LLMProvider.Anthropic,
        LLMProvider.Meta,
        LLMProvider.Alibaba,
        LLMProvider.OpenRouter,
        LLMProvider.Ollama,
        LLMProvider.Bedrock,
        LLMProvider.DeepSeek,
        LLMProvider.MistralAI,
        LLMProvider.OCI,
        LLMProvider.MiniMax,
        LLMProvider.ZhipuAI,
        LLMProvider.HuggingFace,
        LLMProvider.Azure,
        LLMProvider.Vertex,
    ).sortedByDescending { it.id.length }

    private val providersById: Map<String, LLMProvider> = knownProviders.associateBy { it.id }

    /**
     * Resolves the [LLMProvider] for the given [chatModel] and optional explicit [providerId].
     *
     * @param chatModel the Spring AI chat model bean
     * @param providerId optional explicit provider id from configuration property
     * @return the resolved [LLMProvider], never `null`
     */
    fun detect(chatModel: ChatModel, providerId: String?): LLMProvider {
        // 1. Explicit property
        if (providerId != null) {
            return providersById[providerId]
                ?: throw IllegalArgumentException(
                    "Unknown koog.spring.ai.chat.provider='$providerId'. " +
                        "Known providers: ${providersById.keys.sorted()}"
                )
        }

        // 2. Auto-detect: if the lowercase simple class name starts with a known provider id, match it
        val className = chatModel.javaClass.simpleName.lowercase()
        for (provider in knownProviders) {
            if (className.startsWith(provider.id)) {
                return provider
            }
        }

        // 3. Fallback
        return SpringAiLLMProvider
    }
}
