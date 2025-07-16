package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
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
 * Creates a subgraph with retry mechanism, allowing a specified action subgraph to be retried multiple
 * times until a given condition is met or the maximum number of retries is reached.
 *
 * @param condition A function that evaluates whether the output meets the desired condition.
 * @param maxRetries The maximum number of allowed retries. Must be greater than 0.
 * @param toolSelectionStrategy The strategy used to select a tool for executing the action.
 * @param name The optional name of the subgraph.
 * @param defineAction A lambda defining the action subgraph to perform within the retry subgraph.
 */
public inline fun <reified Input : Any, reified Output> AIAgentSubgraphBuilderBase<*, *>.subgraphWithRetry(
    noinline condition: suspend (Output) -> Boolean,
    maxRetries: Int,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    name: String? = null,
    noinline defineAction: AIAgentSubgraphBuilderBase<Input, Output>.() -> Unit,
): AIAgentSubgraphDelegate<Input, RetrySubgraphResult<Output>> {
    require(maxRetries > 0) { "maxRetries must be greater than 0" }

    return subgraph(name = name) {
        val retriesKey = createStorageKey<Int>("${name}_retires")
        val initialInputKey = createStorageKey<Any>("${name}_initial_input")
        val initialContextKey = createStorageKey<AIAgentContextBase>("${name}_initial_context")

        val beforeAction by node<Input, Input> { input ->
            val retries = storage.get(retriesKey) ?: 0

            // Store initial input on the first run
            if (retries == 0) {
                storage.set(initialInputKey, input)
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
            val success = condition(output)

            RetrySubgraphResult(
                output = output,
                success = success,
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
 * @param toolSelectionStrategy The strategy used to select tools for this subgraph.
 * @param strict If true, an exception is thrown if the condition is not met after the maximum retries.
 * @param name An optional name for the subgraph.
 * @param defineAction A lambda defining the actions within the subgraph.
 *
 * Example usage:
 * ```
 * val subgraphRetryCallLLM by subgraphWithRetrySimple(
 *     condition = { it is Message.Tool.Call},
 *     maxRetries = 2,
 * ) {
 *     val nodeCallLLM by nodeLLMRequest("sendInput")
 *     nodeStart then nodeCallLLM then nodeFinish
 * }
 * val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
 * edge(subgraphRetryCallLLM forwardTo nodeExecuteTool onToolCall { true })
 * ```
 */
public inline fun <reified Input : Any, reified Output> AIAgentSubgraphBuilderBase<*, *>.subgraphWithRetrySimple(
    noinline condition: suspend (Output) -> Boolean,
    maxRetries: Int,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    strict: Boolean = true,
    name: String? = null,
    noinline defineAction: AIAgentSubgraphBuilderBase<Input, Output>.() -> Unit,
): AIAgentSubgraphDelegate<Input, Output> {
    return subgraph(name = name) {
        val retrySubgraph by subgraphWithRetry(
            toolSelectionStrategy = toolSelectionStrategy,
            condition = condition,
            maxRetries = maxRetries,
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
