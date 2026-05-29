package ai.koog.prompt.executor.selection

public sealed interface SelectionExecutionPolicy

public object TryBest : SelectionExecutionPolicy

public open class TryUpTo(public val maxModelsToTry: Int) : SelectionExecutionPolicy

public object TryAll : SelectionExecutionPolicy
