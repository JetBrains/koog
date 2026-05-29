package ai.koog.prompt.executor.model

import ai.koog.prompt.llm.LLModel

/**
 * The model selected by [PromptExecutor.resolveModel] for execution
 */
public data class ResolvedModel(public val effectiveModel: LLModel)

public enum class PromptExecutorOperation {
    Execute,
    Moderate,
    MultipleChoices,
    Streaming
}

public class ModelResolutionException(
    requestedModel: LLModel,
    detailedMsg: String? = null,
    cause: Throwable? = null
) : Exception("Model resolution failed for requested model: $requestedModel." + detailedMsg?.let { "\n\t$it" }, cause)
