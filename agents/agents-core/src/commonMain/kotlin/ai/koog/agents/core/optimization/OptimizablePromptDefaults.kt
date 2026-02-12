@file:OptIn(DetachedPromptExecutorAPI::class)

package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.structure.executeStructured
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Builds a [Prompt] from instruction, demonstrations, and input.
 *
 * This function is responsible only for constructing the prompt (the list of messages).
 * The LLM call and response parsing are handled separately by [OptimizableNode].
 *
 * @param TInput The type of input the node receives from the graph.
 * @param TOutput The type of output the node produces for the graph.
 */
public typealias OptimizableNodePromptBuildFn<TInput, TOutput> = (
    instruction: String,
    demos: List<Demonstration<TInput, TOutput>>,
    input: TInput,
) -> Prompt


/**
 * Default prompt function for `String -> String` nodes.
 */
public val defaultStringPromptFn: OptimizableNodePromptBuildFn<String, String> =
    { instruction, demos, input ->
        prompt("optimizable-node") {
            system(instruction)
            for (demo in demos) {
                user(demo.input)
                assistant(demo.output)
            }
            user(input)
        }
    }

/**
 * Default prompt function for generic typed nodes using JSON serialization.
 *
 * Serializes demonstration inputs/outputs and the node input to JSON for the prompt messages.
 *
 * @param TInput The input type (must be `@Serializable`).
 * @param TOutput The output type (must be `@Serializable`).
 * @param inputSerializer Serializer for input values.
 * @param outputSerializer Serializer for output values.
 */
public fun <TInput, TOutput> defaultPromptFn(
    inputSerializer: KSerializer<TInput>,
    outputSerializer: KSerializer<TOutput>,
): OptimizableNodePromptBuildFn<TInput, TOutput> {
    val json = Json { isLenient = true; ignoreUnknownKeys = true }
    return { instruction, demos, input ->
        prompt("optimizable-node") {
            system(instruction)
            for (demo in demos) {
                user(json.encodeToString(inputSerializer, demo.input))
                assistant(json.encodeToString(outputSerializer, demo.output))
            }
            user(json.encodeToString(inputSerializer, input))
        }
    }
}

/**
 * Default LLM execution for String output: calls the LLM and returns response content.
 */
internal val defaultStringExecutePrompt: suspend AIAgentGraphContextBase.(Prompt) -> String =
    { builtPrompt ->
        llm.promptExecutor.execute(builtPrompt, llm.model).first().content
    }

/**
 * Default LLM execution for typed output: uses structured output to call the LLM and parse
 * the response into [TOutput].
 */
@PublishedApi internal fun <TOutput> defaultStructuredExecutePrompt(
    outputSerializer: KSerializer<TOutput>,
): suspend AIAgentGraphContextBase.(Prompt) -> TOutput =
    { builtPrompt ->
        llm.promptExecutor.executeStructured(builtPrompt, llm.model, outputSerializer).getOrThrow().data
    }
