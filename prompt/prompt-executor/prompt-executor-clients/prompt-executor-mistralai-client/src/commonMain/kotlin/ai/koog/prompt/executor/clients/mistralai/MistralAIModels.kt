package ai.koog.prompt.executor.clients.mistralai

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels.MISTRAL_MEDIUM_3_1
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

public object MistralAIModels : LLModelDefinitions {

    public val MISTRAL_MEDIUM_3_1: LLModel = LLModel(
        provider = LLMProvider.MistralAI,
        id = "mistral-medium-2508",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Completion,
            LLMCapability.Tools
        ),
        contextLength = 131_072
    )

}

internal val DEFAULT_MISTRAL_AI_MODEL_VERSIONS_MAP: Map<LLModel, String> = mapOf(
    MISTRAL_MEDIUM_3_1 to "mistral-medium-2508"
)
