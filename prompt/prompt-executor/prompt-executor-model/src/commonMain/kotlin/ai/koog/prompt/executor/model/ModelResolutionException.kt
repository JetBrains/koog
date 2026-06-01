package ai.koog.prompt.executor.model

import ai.koog.prompt.llm.LLModel

/**
 * Exception thrown when model resolution ([PromptExecutorAPI.resolveModel]) fails for a requested model.
 */
public class ModelResolutionException(
    requestedModel: LLModel,
    detailedMsg: String? = null,
    cause: Throwable? = null
) : Exception("Model resolution failed for requested model: $requestedModel." + detailedMsg?.let { "\n\t$it" }, cause)
