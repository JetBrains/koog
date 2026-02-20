@file:OptIn(DetachedPromptExecutorAPI::class)

package ai.koog.agents.core.optimization.core

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.structure.executeStructured
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
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
 * Extracts field names and their [@LLMDescription] annotations from a [SerialDescriptor].
 * Returns an empty list if no fields have descriptions.
 */
internal fun extractFieldDescriptions(descriptor: SerialDescriptor): List<Pair<String, String>> {
    return (0 until descriptor.elementsCount).mapNotNull { i ->
        val name = descriptor.getElementName(i)
        val desc = descriptor.getElementAnnotations(i)
            .filterIsInstance<LLMDescription>()
            .firstOrNull()
            ?.description
        if (desc != null) name to desc else null
    }
}

/**
 * Builds a system message from the instruction and optional input field descriptions.
 * If the input type has [@LLMDescription] annotations on its fields, they are appended
 * to the instruction as an "Input fields:" section, giving the LLM context about
 * what each JSON field means.
 */
private fun buildSystemMessage(instruction: String, inputFieldDescriptions: List<Pair<String, String>>): String {
    if (inputFieldDescriptions.isEmpty()) return instruction
    return buildString {
        append(instruction)
        append("\n\nInput fields:\n")
        inputFieldDescriptions.forEach { (name, desc) ->
            append("- $name: $desc\n")
        }
    }
}

/**
 * Default prompt function for generic typed nodes using JSON serialization.
 *
 * Serializes demonstration inputs/outputs and the node input to JSON for the prompt messages.
 * If the input type has [@LLMDescription] annotations on its fields, they are included
 * in the system message alongside the instruction.
 *
 * @param TInput The input type (must be `@Serializable`).
 * @param TOutput The output type (must be `@Serializable`).
 * @param inputSerializer Serializer for input values.
 * @param outputSerializer Serializer for output values.
 */
public fun <TInput, TOutput> defaultPromptFn(
    inputSerializer: KSerializer<TInput>,
    outputSerializer: KSerializer<TOutput>,
    includeFieldDescriptions: Boolean = false,
): OptimizableNodePromptBuildFn<TInput, TOutput> {
    val json = Json { prettyPrint = false; isLenient = true; ignoreUnknownKeys = true }
    val inputFieldDescriptions = if (includeFieldDescriptions)
        extractFieldDescriptions(inputSerializer.descriptor)
    else
        emptyList()
    return { instruction, demos, input ->
        prompt("optimizable-node") {
            system(buildSystemMessage(instruction, inputFieldDescriptions))
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
