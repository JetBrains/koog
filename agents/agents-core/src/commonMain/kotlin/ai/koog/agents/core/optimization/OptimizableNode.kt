@file:OptIn(DetachedPromptExecutorAPI::class)

package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.OptimizationConfig
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.structure.executeStructured
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.coroutines.coroutineContext
import kotlin.jvm.JvmName
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Builds a [Prompt] from instruction, demonstrations, and input.
 *
 * This function is responsible only for constructing the prompt (the list of messages).
 * The LLM call and response parsing are handled separately by [OptimizableNode].
 *
 * @param TInput The type of input the node receives from the graph.
 * @param TOutput The type of output the node produces for the graph.
 */
public typealias OptimizablePromptFn<TInput, TOutput> = (
    instruction: String,
    demos: List<Demonstration<TInput, TOutput>>,
    input: TInput,
) -> Prompt


/**
 * Default prompt function for `String -> String` nodes.
 */
public val defaultStringPromptFn: OptimizablePromptFn<String, String> =
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
): OptimizablePromptFn<TInput, TOutput> {
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

/**
 * A node with built-in optimization support for MIPRO-style prompt optimization.
 *
 * Unlike a regular [AIAgentNode], an [OptimizableNode] declares which fields of an
 * [Example][ai.koog.agents.core.optimization.core.Example] map to its input and output.
 * This allows optimizers like [LabeledFewShot][ai.koog.agents.core.optimization.optimizers.LabeledFewShot]
 * to create per-node [Demonstration]s with the correct field values.
 *
 * The node separates prompt construction from LLM execution:
 * - [promptFn] builds a [Prompt] from instruction + demonstrations + input
 * - The node handles the LLM call and response parsing (using structured output for typed nodes)
 *
 * At execution time:
 * 1. Resolves the effective instruction (from [OptimizationConfig] context or [instruction] default)
 * 2. Resolves demonstrations (from context or [demonstrations] default)
 * 3. Calls [promptFn] to build the [Prompt]
 * 4. Executes the prompt against the LLM and parses the response
 *
 * Usage:
 * ```kotlin
 * // String -> String (default prompt construction):
 * val classify by optimizableNode(
 *     instruction = "Classify the sentiment of the text.",
 *     inputField = "text",
 *     outputField = "sentiment",
 * )
 *
 * // With pre-existing demonstrations:
 * val classify by optimizableNode(
 *     instruction = "Classify the sentiment.",
 *     inputField = "text",
 *     outputField = "sentiment",
 *     demonstrations = listOf(
 *         Demonstration("I love it!", "positive"),
 *         Demonstration("Terrible.", "negative"),
 *     ),
 * )
 *
 * // Custom types (uses JSON prompt + structured output by default):
 * val classify by optimizableNode<String, Sentiment>(
 *     instruction = "Classify the sentiment.",
 *     inputField = "text",
 *     outputField = "sentiment",
 * )
 * ```
 *
 * @param TInput The type of input this node receives from the graph.
 * @param TOutput The type of output this node produces for the graph.
 * @property inputField The key in [Example.data][ai.koog.agents.core.optimization.core.Example.data]
 *  that provides this node's input. Null if this node doesn't map to Example fields
 *  (e.g., when demonstrations are provided directly).
 * @property outputField The key in [Example.data][ai.koog.agents.core.optimization.core.Example.data]
 *  that provides this node's expected output. Null if this node doesn't map to Example fields.
 * @property instruction The base instruction for prompt construction. May be overridden at
 *  runtime via [OptimizationConfig] in the coroutine context.
 * @property promptFn The prompt construction function that builds a [Prompt] from instruction,
 *  demonstrations, and input.
 * @property demonstrations Default demonstrations for few-shot prompting. May be overridden at
 *  runtime via [OptimizationConfig] in the coroutine context.
 * @property description Optional description for MIPRO program description.
 */
public class OptimizableNode<TInput, TOutput> internal constructor(
    name: String,
    public val inputField: String?,
    public val outputField: String?,
    public val instruction: String,
    public val promptFn: OptimizablePromptFn<TInput, TOutput>,
    internal val executePrompt: suspend AIAgentGraphContextBase.(Prompt) -> TOutput,
    inputType: KType,
    outputType: KType,
    public val description: String? = null,
    public val demonstrations: List<Demonstration<TInput, TOutput>> = emptyList(),
) : AIAgentNode<TInput, TOutput>(
    name = name,
    inputType = inputType,
    outputType = outputType,
    execute = { input ->
        val config = coroutineContext[OptimizationConfig]
        val effectiveInstruction = config?.getInstruction(name) ?: instruction
        @Suppress("UNCHECKED_CAST")
        val effectiveDemos = config?.getTypedDemonstrations<TInput, TOutput>(name) ?: demonstrations
        val builtPrompt = promptFn(effectiveInstruction, effectiveDemos, input)
        executePrompt(builtPrompt)
    },
)

/**
 * Property delegate that creates an [OptimizableNode].
 *
 * The node name is resolved lazily — either from an explicit name or from the delegated property name.
 * This is safe because graph construction (which triggers [getValue]) always happens before execution.
 *
 * @param TInput The type of input the node receives from the graph.
 * @param TOutput The type of output the node produces for the graph.
 */
public class OptimizableNodeDelegate<TInput, TOutput>(
    private val name: String?,
    private val inputField: String?,
    private val outputField: String?,
    private val instruction: String,
    private val description: String?,
    private val demonstrations: List<Demonstration<TInput, TOutput>>,
    private val promptFn: OptimizablePromptFn<TInput, TOutput>,
    private val executePrompt: suspend AIAgentGraphContextBase.(Prompt) -> TOutput,
    private val inputType: KType,
    private val outputType: KType,
) {
    private var optimizableNode: OptimizableNode<TInput, TOutput>? = null

    /**
     * Creates (or returns cached) the [OptimizableNode], deriving the name from [property] if not explicit.
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentNodeBase<TInput, TOutput> {
        if (optimizableNode == null) {
            optimizableNode = OptimizableNode(
                name = name ?: property.name,
                inputField = inputField,
                outputField = outputField,
                instruction = instruction,
                promptFn = promptFn,
                executePrompt = executePrompt,
                inputType = inputType,
                outputType = outputType,
                description = description,
                demonstrations = demonstrations,
            )
        }
        return optimizableNode!!
    }
}

/**
 * Creates an optimizable `String -> String` node.
 *
 * Uses [defaultStringPromptFn] for prompt construction (raw text messages) and
 * regular LLM execution returning response content.
 *
 * Example:
 * ```kotlin
 * val classify by optimizableNode(
 *     instruction = "Classify the sentiment.",
 *     inputField = "text",
 *     outputField = "sentiment",
 * )
 * ```
 *
 * @param instruction The base instruction for prompt construction.
 * @param inputField The key in Example.data that provides this node's input. Null if not mapping to Examples.
 * @param outputField The key in Example.data that provides this node's expected output. Null if not mapping to Examples.
 * @param name Explicit node name. If null, derived from the delegated property name.
 * @param description Optional description for MIPRO program description.
 * @param demonstrations Default demonstrations for few-shot prompting.
 * @param promptFn Custom prompt function. Defaults to [defaultStringPromptFn].
 * @return An [OptimizableNodeDelegate] for use with Kotlin property delegation (`by`).
 */
public fun AIAgentSubgraphBuilderBase<*, *>.optimizableNode(
    instruction: String,
    inputField: String? = null,
    outputField: String? = null,
    name: String? = null,
    description: String? = null,
    demonstrations: List<Demonstration<String, String>> = emptyList(),
    promptFn: OptimizablePromptFn<String, String> = defaultStringPromptFn,
): OptimizableNodeDelegate<String, String> {
    return OptimizableNodeDelegate(
        name = name,
        inputField = inputField,
        outputField = outputField,
        instruction = instruction,
        description = description,
        demonstrations = demonstrations,
        promptFn = promptFn,
        executePrompt = defaultStringExecutePrompt,
        inputType = typeOf<String>(),
        outputType = typeOf<String>(),
    )
}

/**
 * Creates an optimizable node with custom types.
 *
 * Uses [defaultPromptFn] for prompt construction (JSON-encoded messages) and
 * structured output for LLM execution and response parsing.
 *
 * Example:
 * ```kotlin
 * // With default JSON prompt + structured output:
 * val classify by optimizableNode<String, Sentiment>(
 *     instruction = "Classify the sentiment.",
 *     inputField = "text",
 *     outputField = "sentiment",
 * )
 *
 * // With custom prompt function:
 * val classify by optimizableNode<String, Sentiment>(
 *     instruction = "Classify the sentiment.",
 *     inputField = "text",
 *     outputField = "sentiment",
 *     promptFn = { instruction, demos, input ->
 *         prompt("classify") {
 *             system(instruction)
 *             user(input)
 *         }
 *     }
 * )
 * ```
 *
 * @param TInput The type of input the node receives (must be `@Serializable` if using default promptFn).
 * @param TOutput The type of output the node produces (must be `@Serializable`; used for structured output).
 * @param instruction The base instruction for prompt construction.
 * @param inputField The key in Example.data that provides this node's input. Null if not mapping to Examples.
 * @param outputField The key in Example.data that provides this node's expected output. Null if not mapping to Examples.
 * @param name Explicit node name. If null, derived from the delegated property name.
 * @param description Optional description for MIPRO program description.
 * @param demonstrations Default demonstrations for few-shot prompting.
 * @param promptFn Custom prompt function. Defaults to [defaultPromptFn] with JSON serialization.
 * @return An [OptimizableNodeDelegate] for use with Kotlin property delegation (`by`).
 */
@JvmName("optimizableNodeTyped")
public inline fun <reified TInput, reified TOutput> AIAgentSubgraphBuilderBase<*, *>.optimizableNode(
    instruction: String,
    inputField: String? = null,
    outputField: String? = null,
    name: String? = null,
    description: String? = null,
    demonstrations: List<Demonstration<TInput, TOutput>> = emptyList(),
    noinline promptFn: OptimizablePromptFn<TInput, TOutput> = defaultPromptFn(serializer<TInput>(), serializer<TOutput>()),
): OptimizableNodeDelegate<TInput, TOutput> {
    return OptimizableNodeDelegate(
        name = name,
        inputField = inputField,
        outputField = outputField,
        instruction = instruction,
        description = description,
        demonstrations = demonstrations,
        promptFn = promptFn,
        executePrompt = defaultStructuredExecutePrompt(serializer<TOutput>()),
        inputType = typeOf<TInput>(),
        outputType = typeOf<TOutput>(),
    )
}
