package ai.koog.prompt.executor.clients.retrollmfit

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.Serializable

/** Provider singleton for all RetroLLMFit-generated clients. */
@Serializable
public object RetroLLMFitProvider : LLMProvider("retrollmfit", "RetroLLMFit")

/**
 * Generic model placeholder used with [RetroLLMFit]-generated clients.
 * The actual model is determined by the annotated server endpoint, not by this object.
 */
public val RetroLLMFitModel: LLModel = LLModel(
    provider = RetroLLMFitProvider,
    id = "retrollmfit",
    capabilities = listOf(LLMCapability.Completion),
    contextLength = null,
    maxOutputTokens = null,
)
