package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo

/**
 * Represents the result of [subgraphWithRetry].
 *
 * @param output The result of the subgraph operation.
 * @param success A boolean indicating whether the action was successful.
 * @param retryCount The number of retries attempted.
 */
public data class RetrySubgraphResult<Output>(
    val output: Output,
    val success: Boolean,
    val retryCount: Int,
) {
    init {
        require(retryCount > 0) { "retryCount must be greater than 0" }
    }
}

/**
 * Represents the result of evaluating a specific condition in a system or workflow.
 * This sealed interface allows for expressing various outcomes of a condition check.
 *
 * Implementations:
 * - [Approve]: Indicates that the condition was approved.
 * - [Reject]: Indicates that the condition was rejected, optionally with feedback.
 */
public sealed interface ConditionResult {
    /**
     * Indicates whether the current instance of [ConditionResult] represents a successful state.
     * Returns `true` if the instance is of type [Approve], signifying success.
     * Otherwise, returns `false`.
     */
    public val isSuccess: Boolean get() = this is Approve

    /**
     * Object representing an approved condition result.
     *
     * This implementation of the [ConditionResult] interface indicates that a retry condition has succeeded.
     */
    public object Approve : ConditionResult

    /**
     * Represents a condition result indicating rejection, optionally with feedback.
     *
     * This implementation of the [ConditionResult] interface indicates that a retry condition has failed.
     * It can contain optional [feedback] parameter which will be passed to the llm for the next retries.
     *
     * @property feedback An optional descriptive message or information explaining the reason for the rejection.
     */
    public class Reject(public val feedback: String? = null) : ConditionResult
}

/**
 * Extension property that converts a Boolean to a ConditionResult.
 * - true is converted to ConditionResult.Approve
 * - false is converted to ConditionResult.Reject
 *
 * This allows for explicit conversion from Boolean to ConditionResult.
 */
public val Boolean.asConditionResult: ConditionResult
    get() = if (this) ConditionResult.Approve else ConditionResult.Reject()

/**
 * Creates a subgraph with retry mechanism, allowing a specified action subgraph to be retried multiple
 * times until a given condition is met or the maximum number of retries is reached.
 *
 * @param condition A function that evaluates whether the output meets the desired condition.
 * @param maxRetries The maximum number of allowed retries. Must be greater than 0.
 * @param conditionDescription A message which explains the condition constraints to the model
 * @param toolSelectionStrategy The strategy used to select a tool for executing the action.
 * @param name The optional name of the subgraph.
 * @param defineAction A lambda defining the action subgraph to perform within the retry subgraph.
 */
@AIAgentBuilderDslMarker
public inline fun <reified Input : Any, reified Output> AIAgentSubgraphBuilderBase<*, *>.subgraphWithRetry(
    noinline condition: suspend AIAgentGraphContextBase.(Output) -> ConditionResult,
    maxRetries: Int,
    conditionDescription: String? = null,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    name: String? = null,
    noinline defineAction: AIAgentSubgraphBuilderBase<Input, Output>.() -> Unit,
): AIAgentSubgraphDelegate<Input, RetrySubgraphResult<Output>> {
    require(maxRetries > 0) { "maxRetries must be greater than 0" }

    return subgraph(name = name) {
        val retriesKey = createStorageKey<Int>("${name}_retires")
        val initialInputKey = createStorageKey<Any>("${name}_initial_input")
        val initialContextKey = createStorageKey<AIAgentGraphContextBase>("${name}_initial_context")

        val beforeAction by node<Input, Input> { input ->
            val retries = storage.get(retriesKey) ?: 0

            // Store initial input and clarification message on the first run
            if (retries == 0) {
                storage.set(initialInputKey, input)

                if (conditionDescription != null) {
                    llm.writeSession {
                        updatePrompt {
                            user(conditionDescription)
                        }
                    }
                }
            } else {
                // return the initial context
                this.replace(storage.getValue(initialContextKey))
            }
            // store the initial context
            storage.set(initialContextKey, this.fork())

            // Increment retries
            storage.set(retriesKey, retries + 1)

            input
        }

        val actionSubgraph by subgraph(
            name = "${name}_retryableAction",
            toolSelectionStrategy = toolSelectionStrategy,
            define = defineAction
        )

        val decide by node<Output, RetrySubgraphResult<Output>> { output ->
            val retries = storage.getValue(retriesKey)

            // fork the context before applying the condition
            // so that user can update prompt and call llm
            // to determine if the condition is satisfied
            val conditionResult = fork().condition(output)
            if (conditionResult is ConditionResult.Reject && conditionResult.feedback != null) {
                // Update the prompt if feedback is provided
                storage.getValue(initialContextKey).llm.writeSession {
                    updatePrompt {
                        user(conditionResult.feedback)
                    }
                }
            }

            RetrySubgraphResult(
                output = output,
                success = conditionResult.isSuccess,
                retryCount = retries
            )
        }

        val cleanup by node<RetrySubgraphResult<Output>, RetrySubgraphResult<Output>> { result ->
            storage.remove(retriesKey)
            storage.remove(initialInputKey)
            storage.remove(initialContextKey)
            result
        }

        nodeStart then beforeAction then actionSubgraph then decide

        // Repeat the action with initial input when condition is not met and the number of retries does not exceed max retries.
        edge(
            decide forwardTo beforeAction
                onCondition { result -> !result.success && result.retryCount < maxRetries }
                transformed {
                    @Suppress("UNCHECKED_CAST")
                    storage.getValue(initialInputKey) as Input
                }
        )

        // Otherwise return the last iteration result.
        edge(
            decide forwardTo cleanup
                onCondition { result -> result.success || result.retryCount >= maxRetries }
        )

        cleanup then nodeFinish
    }
}

/**
 * Creates a subgraph that includes retry functionality based on a given condition and a maximum number of retries.
 * If the condition is not met after the specified retries and strict mode is enabled, an exception is thrown.
 * Unlike [subgraphWithRetry], this function directly returns the output value instead of a [RetrySubgraphResult].
 *
 * @param condition A suspendable function that determines whether the condition is met, based on the output.
 * @param maxRetries The maximum number of retries allowed if the condition is not met.
 * @param conditionDescription A message which explains the condition constraints to the model
 * @param toolSelectionStrategy The strategy used to select tools for this subgraph.
 * @param strict If true, an exception is thrown if the condition is not met after the maximum retries.
 * @param name An optional name for the subgraph.
 * @param defineAction A lambda defining the actions within the subgraph.
 *
 * Example usage:
 * ```
 * val subgraphRetryCallLLM by subgraphWithRetrySimple(
 *     condition = { (it is Message.Tool.Call).asConditionResult },
 *     maxRetries = 2,
 * ) {
 *     val nodeCallLLM by nodeLLMRequest("sendInput")
 *     nodeStart then nodeCallLLM then nodeFinish
 * }
 * val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
 * edge(subgraphRetryCallLLM forwardTo nodeExecuteTool onToolCall { true })
 * ```
 */
@AIAgentBuilderDslMarker
public inline fun <reified Input : Any, reified Output> AIAgentSubgraphBuilderBase<*, *>.subgraphWithRetrySimple(
    noinline condition: suspend AIAgentGraphContextBase.(Output) -> ConditionResult,
    maxRetries: Int,
    conditionDescription: String? = null,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    strict: Boolean = true,
    name: String? = null,
    noinline defineAction: AIAgentSubgraphBuilderBase<Input, Output>.() -> Unit,
): AIAgentSubgraphDelegate<Input, Output> {
    return subgraph(name = name) {
        val retrySubgraph by subgraphWithRetry(
            condition = condition,
            maxRetries = maxRetries,
            conditionDescription = conditionDescription,
            toolSelectionStrategy = toolSelectionStrategy,
            name = name,
            defineAction = defineAction
        )

        val extractResult by node<RetrySubgraphResult<Output>, Output> { result ->
            if (strict && !result.success) {
                throw IllegalStateException("Failed to meet condition after ${result.retryCount} retries")
            }
            result.output
        }

        nodeStart then retrySubgraph then extractResult then nodeFinish
    }
}
