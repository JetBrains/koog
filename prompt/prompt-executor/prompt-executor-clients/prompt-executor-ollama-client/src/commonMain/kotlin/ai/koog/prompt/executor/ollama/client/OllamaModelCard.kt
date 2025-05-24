package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

@ConsistentCopyVisibility
public data class OllamaModelCard internal constructor(
    val name: String,
    val family: String,
    val families: List<String>?,
    val size: Long,
    val parameterCount: Long?,
    val contextLength: Long?,
    val embeddingLength: Long?,
    val quantizationLevel: String?,
    val capabilities: List<LLMCapability>,
) {
    val nameWithoutTag: String get() = name.substringBeforeLast(":")
}

public fun OllamaModelCard.toLLModel(): LLModel = LLModel(
    provider = LLMProvider.Ollama,
    id = name,
    capabilities = capabilities,
)
