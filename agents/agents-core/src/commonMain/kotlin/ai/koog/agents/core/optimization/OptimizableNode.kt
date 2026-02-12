package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.OptimizationConfig
import ai.koog.agents.core.optimization.dsl.getOptimizationConfig
import ai.koog.prompt.dsl.Prompt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A node with built-in optimization support for MIPRO-style prompt optimization.
 *
 * Unlike a regular [AIAgentNode], an [OptimizableNode] declares its instruction and
 * demonstrations as first-class properties that can be overridden at runtime via
 * [OptimizationConfig] in the coroutine context. This allows optimizers like
 * [BootstrapFewShot][ai.koog.agents.core.optimization.optimizers.BootstrapFewShot]
 * to swap in optimized prompts without modifying the graph.
 *
 * The node separates prompt construction from LLM execution:
 * - [promptFn] builds a [Prompt] from instruction + demonstrations and input
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
 * )
 *
 * // With pre-existing demonstrations:
 * val classify by optimizableNode(
 *     instruction = "Classify the sentiment.",
 *     demonstrations = listOf(
 *         Demonstration("I love it!", "positive"),
 *         Demonstration("Terrible.", "negative"),
 *     ),
 * )
 *
 * // Custom types (uses JSON prompt + structured output by default):
 * val classify by optimizableNode<String, Sentiment>(
 *     instruction = "Classify the sentiment.",
 * )
 * ```
 *
 * @param TInput The type of input this node receives from the graph.
 * @param TOutput The type of output this node produces for the graph.
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
    public val instruction: String,
    public val promptFn: OptimizableNodePromptBuildFn<TInput, TOutput>,
    internal val executePrompt: suspend AIAgentGraphContextBase.(Prompt) -> TOutput,
    inputType: KType,
    outputType: KType,
    public val description: String? = null,
    public val demonstrations: List<Demonstration<TInput, TOutput>> = emptyList(),
) : AIAgentNode<TInput, TOutput>(
    name = name,
    inputType = inputType,
    outputType = outputType,
    // TODO: Currently, if one wants to optimize w/ respect to params other than instruction/demos,
    //  they must change OptimizableNode + Optimizer + OptimizationConfig.
    //  We might want the Optimizer to be able to extend OptimizationConfig on demand.
    //  This however, will over-complicate the API, but we may try, if introducing new optimization
    //  parameters is a common use case and will be too complex.
    execute = { input ->
        // Demonstrations and instruction may be overridden at runtime
        // via OptimizationConfig passed through coroutine context.
        val config = getOptimizationConfig()
        // Resolve from the coroutine context or fallback to defaults.
        val effectiveInstruction = config?.getInstruction(name) ?: instruction
        val effectiveDemos = config?.getTypedDemonstrations(name) ?: demonstrations

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
    private val instruction: String,
    private val description: String?,
    private val demonstrations: List<Demonstration<TInput, TOutput>>,
    private val promptFn: OptimizableNodePromptBuildFn<TInput, TOutput>,
    private val executePrompt: suspend AIAgentGraphContextBase.(Prompt) -> TOutput,
    private val inputType: KType,
    private val outputType: KType,
) {
    private var optimizableNode: OptimizableNode<TInput, TOutput>? = null

    /**
     * Creates (or returns cached) the [OptimizableNode], deriving the name from [property] if not explicit.
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentNodeBase<TInput, TOutput> {
        return optimizableNode ?: OptimizableNode(
            name = name ?: property.name,
            instruction = instruction,
            promptFn = promptFn,
            executePrompt = executePrompt,
            inputType = inputType,
            outputType = outputType,
            description = description,
            demonstrations = demonstrations,
        ).also { optimizableNode = it }
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
 * )
 * ```
 *
 * @param instruction The base instruction for prompt construction.
 * @param name Explicit node name. If null, derived from the delegated property name.
 * @param description Optional description for MIPRO program description.
 * @param demonstrations Default demonstrations for few-shot prompting.
 * @param promptFn Custom prompt function. Defaults to [defaultStringPromptFn].
 * @return An [OptimizableNodeDelegate] for use with Kotlin property delegation (`by`).
 */
@Suppress("UnusedReceiverParameter")
public fun AIAgentSubgraphBuilderBase<*, *>.optimizableNode(
    instruction: String,
    name: String? = null,
    description: String? = null,
    demonstrations: List<Demonstration<String, String>> = emptyList(),
    promptFn: OptimizableNodePromptBuildFn<String, String> = defaultStringPromptFn,
): OptimizableNodeDelegate<String, String> {
    return OptimizableNodeDelegate(
        name = name,
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
 * )
 *
 * // With custom prompt function:
 * val classify by optimizableNode<String, Sentiment>(
 *     instruction = "Classify the sentiment.",
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
 * @param name Explicit node name. If null, derived from the delegated property name.
 * @param description Optional description for MIPRO program description.
 * @param demonstrations Default demonstrations for few-shot prompting.
 * @param promptFn Custom prompt function. Defaults to [defaultPromptFn] with JSON serialization.
 * @return An [OptimizableNodeDelegate] for use with Kotlin property delegation (`by`).
 */
@JvmName("optimizableNodeTyped")
public inline fun <reified TInput, reified TOutput> AIAgentSubgraphBuilderBase<*, *>.optimizableNode(
    instruction: String,
    name: String? = null,
    description: String? = null,
    demonstrations: List<Demonstration<TInput, TOutput>> = emptyList(),
    noinline promptFn: OptimizableNodePromptBuildFn<TInput, TOutput> = defaultPromptFn(serializer<TInput>(), serializer<TOutput>()),
): OptimizableNodeDelegate<TInput, TOutput> {
    return OptimizableNodeDelegate(
        name = name,
        instruction = instruction,
        description = description,
        demonstrations = demonstrations,
        promptFn = promptFn,
        executePrompt = defaultStructuredExecutePrompt(serializer<TOutput>()),
        inputType = typeOf<TInput>(),
        outputType = typeOf<TOutput>(),
    )
}
