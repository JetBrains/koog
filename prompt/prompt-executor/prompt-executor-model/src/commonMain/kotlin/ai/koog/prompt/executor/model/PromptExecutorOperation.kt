package ai.koog.prompt.executor.model

/**
 * Identifies which [PromptExecutor] operation is being performed. Passed to
 * [PromptExecutorBuilder.resolveModel] so implementations can resolve a different actual model
 * per operation (for example, a fallback model may apply to [Execute] but not [Streaming]).
 */
public enum class PromptExecutorOperation {

    /**
     * Corresponds to [PromptExecutorAPI.execute].
     */
    Execute,

    /**
     * Corresponds to [PromptExecutorAPI.executeStreaming].
     */
    Streaming,

    /**
     * Corresponds to [PromptExecutorAPI.moderate].
     */
    Moderate,

    /**
     * Corresponds to [PromptExecutorAPI.executeMultipleChoices].
     */
    MultipleChoices,
}
