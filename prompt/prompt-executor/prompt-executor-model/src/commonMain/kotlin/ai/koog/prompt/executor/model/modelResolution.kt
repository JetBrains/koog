package ai.koog.prompt.executor.model

import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmInline

/**
 * The model selected by [PromptExecutor.resolveModel] for execution; only the effective model is carried —
 * the originally requested model and the [PromptExecutorOperation] remain in scope at the call site.
 */
@JvmInline
public value class ResolvedModel(public val effectiveModel: LLModel)

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
