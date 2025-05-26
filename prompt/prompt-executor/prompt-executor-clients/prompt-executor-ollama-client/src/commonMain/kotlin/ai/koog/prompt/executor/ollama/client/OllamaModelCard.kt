package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

public class OllamaModelCard internal constructor(
    public val name: String,
    public val family: String,
    public val families: List<String>?,
    public val size: Long,
    public val parameterCount: Long?,
    public val contextLength: Long?,
    public val embeddingLength: Long?,
    public val quantizationLevel: String?,
    public val capabilities: List<LLMCapability>,
)

public val OllamaModelCard.nameWithoutTag: String get() = name.withoutTag

public fun OllamaModelCard.toLLModel(): LLModel = LLModel(
    provider = LLMProvider.Ollama,
    id = name,
    capabilities = capabilities,
)
