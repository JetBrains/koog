package ai.koog.prompt.executor.model

/**
 * Enum representing different operations that can be performed by the prompt executor.
 */
public enum class PromptExecutorOperation {
    /**
     * Represents [PromptExecutorAPI.execute] operation
     */
    Execute,

    /**
     * Represents [PromptExecutorAPI.moderate] operation
     */
    Moderate,

    /**
     * Represents [PromptExecutorAPI.executeMultipleChoices] operation
     */
    MultipleChoices,

    /**
     * Represents [PromptExecutorAPI.executeStreaming] operation
     */
    Streaming
}
