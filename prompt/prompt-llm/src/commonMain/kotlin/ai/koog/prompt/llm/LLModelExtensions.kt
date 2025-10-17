package ai.koog.prompt.llm

import ai.koog.agents.utils.ModelInfo

/**
 * Converts this LLModel to a ModelInfo instance
 */
public fun LLModel.toModelInfo(): ModelInfo = ModelInfo(
    provider = provider.id,
    model = id,
    displayName = provider.display,
    contextLength = contextLength,
    maxOutputTokens = maxOutputTokens
)
