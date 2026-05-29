package ai.koog.prompt.executor.selection

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorOperation
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

public object PromptExecutorExtensions {

    private val logger = KotlinLogging.logger { }

    /**
     * Executes [prompt] using the highest-ranked model from [selection].
     */
    public suspend fun PromptExecutor.execute(
        prompt: Prompt,
        tools: List<ToolDescriptor> = emptyList(),
        selectionExecutionPolicy: SelectionExecutionPolicy = TryBest,
        selection: DefaultModelSelectorBuilder.() -> Unit,
    ): Message.Assistant = attemptWithSelection(
        selectionExecutionPolicy = selectionExecutionPolicy,
        selection = selection,
        operation = PromptExecutorOperation.Execute
    ) { selectedModel -> execute(prompt, selectedModel, tools) }

    /**
     * Returns multiple independent choices for [prompt] using the highest-ranked model from [selection].
     */
    public suspend fun PromptExecutor.executeMultipleChoices(
        prompt: Prompt,
        tools: List<ToolDescriptor> = emptyList(),
        selectionExecutionPolicy: SelectionExecutionPolicy = TryBest,
        selection: DefaultModelSelectorBuilder.() -> Unit,
    ): LLMChoice = attemptWithSelection(
        selectionExecutionPolicy = selectionExecutionPolicy,
        selection = selection,
        operation = PromptExecutorOperation.MultipleChoices
    ) { selectedModel -> executeMultipleChoices(prompt, selectedModel, tools) }

    public suspend fun PromptExecutor.moderate(
        prompt: Prompt,
        selectionExecutionPolicy: SelectionExecutionPolicy = TryBest,
        selection: DefaultModelSelectorBuilder.() -> Unit,
    ): ModerationResult = attemptWithSelection(
        selectionExecutionPolicy = selectionExecutionPolicy,
        selection = selection,
        operation = PromptExecutorOperation.Moderate
    ) { selectedModel -> moderate(prompt, selectedModel) }

    /**
     * Streams output frames for [prompt] using the highest-ranked model from [selection].
     */
    @OptIn(ExperimentalAtomicApi::class)
    public fun PromptExecutor.executeStreaming(
        prompt: Prompt,
        tools: List<ToolDescriptor> = emptyList(),
        selectionExecutionPolicy: SelectionExecutionPolicy = TryBest,
        selection: DefaultModelSelectorBuilder.() -> Unit,
    ): Flow<StreamFrame> = flow {
        val selector = DefaultModelSelectorBuilder().apply(selection).build()
        val modelsToAttempt = selector.select(models()).modelsToAttempt(selectionExecutionPolicy)
        val failures = mutableListOf<Throwable>()

        modelsToAttempt.forEachIndexed { attempt, model ->
            val frameReceived = AtomicBoolean(false)
            try {
                executeStreaming(prompt, model, tools).collect { frame ->
                    frameReceived.store(true)
                    emit(frame)
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (frameReceived.load()) throw e

                logger.info(e) {
                    "Selection based streaming execution failed before first frame " +
                        "(attempt: ${attempt + 1}, model: $model, operation: ${PromptExecutorOperation.Streaming.name})"
                }
                failures.add(e)
            }
        }

        throw SelectionExecutionException(PromptExecutorOperation.Streaming, failures)
    }

    private suspend fun <T> PromptExecutor.attemptWithSelection(
        selectionExecutionPolicy: SelectionExecutionPolicy,
        selection: DefaultModelSelectorBuilder.() -> Unit,
        operation: PromptExecutorOperation,
        execution: suspend (LLModel) -> T
    ): T {
        val selector = DefaultModelSelectorBuilder().apply(selection).build()
        val modelsToAttempt = selector.select(models()).modelsToAttempt(selectionExecutionPolicy)
        val failures = mutableListOf<Throwable>()
        modelsToAttempt.forEachIndexed { attempt, model ->
            try {
                return execution(model)
            } catch (e: Exception) {
                logger.info(e) { "Selection based execution failed (attempt: ${attempt + 1}, model: $model, operation: ${operation.name})" }
                failures.add(e)
            }
        }
        throw SelectionExecutionException(operation, failures)
    }

    private fun ModelSelection.modelsToAttempt(selectionExecutionPolicy: SelectionExecutionPolicy): List<LLModel> {
        check(!ranked.isEmpty()) { "No models available for selection" }
        return when (selectionExecutionPolicy) {
            is TryBest -> listOf(ranked.first())
            is TryUpTo -> ranked.take(selectionExecutionPolicy.maxModelsToTry)
            is TryAll -> ranked
        }
    }
}
