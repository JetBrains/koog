package ai.koog.prompt.executor.model

import ai.koog.prompt.llm.LLModel

/**
 * Represents a resolved model for use by [PromptExecutorAPI]. Outcome of [PromptExecutorAPI.resolveModel]
 *
 * @property effectiveModel The effective [LLModel] instance that will be used by [PromptExecutorAPI]
 * for performing operations.
 */
public data class ResolvedModel(public val effectiveModel: LLModel)
