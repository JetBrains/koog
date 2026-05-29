package ai.koog.prompt.executor.selection

import ai.koog.prompt.executor.model.PromptExecutorOperation

public class SelectionExecutionException(
    operation: PromptExecutorOperation,
    attemptExceptions: List<Throwable>
) : Exception(
    "${operation.name} failed after ${attemptExceptions.size} attempts:" +
        " ${attemptExceptions.joinToString { it.message ?: "Unknown error" }}"
)
