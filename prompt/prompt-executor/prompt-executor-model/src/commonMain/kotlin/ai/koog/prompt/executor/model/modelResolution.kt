package ai.koog.prompt.executor.model

import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmInline

@JvmInline
public value class ResolvedModel(public val effectiveModel: LLModel)

public enum class PromptExecutorOperation {
    Execute,
    Moderate,
    MultipleChoices,
    Stream
}

public class ModelResolutionException(
    requestedModel: LLModel,
    detailedMsg: String? = null,
    cause: Throwable? = null
) : Exception("Model resolution failed for requested model: $requestedModel." + detailedMsg?.let { "\n\t$it" }, cause)
