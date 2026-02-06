package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.dsl.getNodeDemonstrations
import ai.koog.agents.core.optimization.dsl.getNodeInstruction
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A prompt function that owns how the Koog Prompt is constructed from instruction, demonstrations,
 * and input, then calls the LLM and returns the result.
 *
 * When provided, this replaces the default prompt construction logic of [OptimizableNode].
 * The function receives the effective instruction (possibly overridden by optimization),
 * the current demonstrations, and the node input. It should construct and send the prompt
 * via [AIAgentGraphContextBase.llm] and return the result.
 *
 * @param TInput The type of input the node receives from the graph.
 * @param TOutput The type of output the node produces for the graph.
 */
public typealias OptimizablePromptFn<TInput, TOutput> = suspend AIAgentGraphContextBase.(
    instruction: String,
    demos: List<Demonstration<TInput, TOutput>>,
    input: TInput,
) -> TOutput

/**
 * A node with built-in optimization support for MIPRO-style prompt optimization.
 *
 * Unlike a regular [AIAgentNode], an [OptimizableNode] declares which fields of an [Example][ai.koog.agents.core.optimization.core.Example]
 * map to its input and output. This allows optimizers like [LabeledFewShot][ai.koog.agents.core.optimization.optimizers.LabeledFewShot]
 * to create per-node [Demonstration]s with the correct field values.
 *
 * The node owns prompt construction: it builds a prompt from instruction + demonstrations + input
 * and sends it to the LLM via [AIAgentGraphContextBase.llm]. The default prompt format (for `String, String` nodes) is:
 * ```
 * system(instruction)
 * user(demo1.input) / assistant(demo1.output)
 * user(demo2.input) / assistant(demo2.output)
 * ...
 * user(input)
 * ```
 *
 * For non-String types, provide an [OptimizablePromptFn] that handles type conversion.
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
 * // Custom types (promptFn required):
 * val classify by optimizableNode<String, Sentiment>(
 *     instruction = "Classify the sentiment.",
 *     inputField = "text",
 *     outputField = "sentiment",
 *     promptFn = { instruction, demos, input ->
 *         val response = llm.writeSession {
 *             appendPrompt { system(instruction); user(input) }
 *             requestLLMWithoutTools()
 *         }.content
 *         Sentiment.valueOf(response.trim())
 *     }
 * )
 * ```
 *
 * @param TInput The type of input this node receives from the graph.
 * @param TOutput The type of output this node produces for the graph.
 * @property inputField The key in [Example.data][ai.koog.agents.core.optimization.core.Example.data]
 *  that provides this node's input.
 * @property outputField The key in [Example.data][ai.koog.agents.core.optimization.core.Example.data]
 *  that provides this node's expected output.
 */
public class OptimizableNode<TInput, TOutput> internal constructor(
    name: String,
    public val inputField: String,
    public val outputField: String,
    execute: suspend AIAgentGraphContextBase.(TInput) -> TOutput,
    public val instruction: String,
    inputType: KType,
    outputType: KType,
    public val description: String? = null,
) : AIAgentNode<TInput, TOutput>(
    name = name,
    inputType = inputType,
    outputType = outputType,
    execute = execute,
)

/**
 * Property delegate that creates an [OptimizableNode].
 *
 * The delegate wraps a pre-built execute lambda. At execution time, the lambda:
 * 1. Resolves the effective instruction (from [OptimizationConfig][ai.koog.agents.core.optimization.core.OptimizationConfig] context or default)
 * 2. Resolves demonstrations (from context or default)
 * 3. Constructs and sends the prompt via [AIAgentGraphContextBase.llm]
 * 4. Returns the result
 *
 * The node name is resolved lazily — either from an explicit name or from the delegated property name.
 * This is safe because graph construction (which triggers [getValue]) always happens before execution.
 *
 * @param TInput The type of input the node receives from the graph.
 * @param TOutput The type of output the node produces for the graph.
 */
public class OptimizableNodeDelegate<TInput, TOutput>(
    private val name: String?,
    public val inputField: String,
    public val outputField: String,
    private val instruction: String,
    private val description: String?,
    private val inputType: KType,
    private val outputType: KType,
    private val executeImpl: suspend AIAgentGraphContextBase.(TInput) -> TOutput,
) {
    @PublishedApi internal var resolvedName: String? = name
    private var optimizableNode: OptimizableNode<TInput, TOutput>? = null

    /**
     * Creates (or returns cached) the [OptimizableNode], deriving the name from [property] if not explicit.
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentNodeBase<TInput, TOutput> {
        if (optimizableNode == null) {
            resolvedName = name ?: property.name
            optimizableNode = OptimizableNode(
                name = resolvedName!!,
                inputField = inputField,
                outputField = outputField,
                execute = executeImpl,
                instruction = instruction,
                inputType = inputType,
                outputType = outputType,
                description = description,
            )
        }
        return optimizableNode!!
    }
}

/**
 * Creates an optimizable `String -> String` node with automatic prompt construction and LLM execution.
 *
 * This is the convenience overload for the most common case. No type parameters needed.
 * The node owns how the Koog Prompt is constructed. The LLM call happens via
 * [AIAgentGraphContextBase.llm].
 *
 * Default behavior (no [promptFn]):
 * - Constructs: `system(instruction) + user/assistant demo pairs + user(input)`
 * - Calls `requestLLMWithoutTools()` and returns `content`
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
 * @param inputField The key in Example.data that provides this node's input.
 * @param outputField The key in Example.data that provides this node's expected output.
 * @param name Explicit node name. If null, derived from the delegated property name.
 * @param description Optional description for MIPRO program description.
 * @param promptFn Optional custom prompt function to override default prompt construction.
 * @return An [OptimizableNodeDelegate] for use with Kotlin property delegation (`by`).
 */
public fun AIAgentSubgraphBuilderBase<*, *>.optimizableNode(
    instruction: String,
    inputField: String,
    outputField: String,
    name: String? = null,
    description: String? = null,
    promptFn: OptimizablePromptFn<String, String>? = null,
): OptimizableNodeDelegate<String, String> {
    val delegateRef = object { lateinit var delegate: OptimizableNodeDelegate<String, String> }

    val executeImpl: suspend AIAgentGraphContextBase.(String) -> String = { input ->
        val nodeName = delegateRef.delegate.resolvedName
            ?: error("OptimizableNode name not resolved — graph not built yet?")
        val effectiveInstruction = getNodeInstruction(nodeName, instruction)
        val demos = getNodeDemonstrations<String, String>(nodeName, emptyList())

        if (promptFn != null) {
            promptFn.invoke(this, effectiveInstruction, demos, input)
        } else {
            llm.writeSession {
                appendPrompt {
                    system(effectiveInstruction)
                    for (demo in demos) {
                        user(demo.input)
                        assistant(demo.output)
                    }
                    user(input)
                }
                requestLLMWithoutTools()
            }.content
        }
    }

    return OptimizableNodeDelegate(
        name = name,
        inputField = inputField,
        outputField = outputField,
        instruction = instruction,
        description = description,
        inputType = typeOf<String>(),
        outputType = typeOf<String>(),
        executeImpl = executeImpl,
    ).also { delegateRef.delegate = it }
}

/**
 * Creates an optimizable node with custom types and a required prompt function.
 *
 * Use this overload when the node's graph input/output types are not `String`.
 * The [promptFn] is responsible for prompt construction, LLM call, and response parsing.
 *
 * Demonstrations are `Demonstration<TInput, TOutput>` — the optimizer is responsible for
 * producing correctly-typed demos (e.g., LabeledFewShot produces `Demonstration<String, String>`,
 * BootstrapFewShot captures actual node I/O types).
 *
 * Example:
 * ```kotlin
 * val classify by optimizableNode<String, Sentiment>(
 *     instruction = "Classify the sentiment.",
 *     inputField = "text",
 *     outputField = "sentiment",
 *     promptFn = { instruction, demos, input ->
 *         val response = llm.writeSession {
 *             appendPrompt {
 *                 system(instruction)
 *                 for (demo in demos) { user(demo.input.toString()); assistant(demo.output.name) }
 *                 user(input)
 *             }
 *             requestLLMWithoutTools()
 *         }.content
 *         Sentiment.valueOf(response.trim())
 *     }
 * )
 * ```
 *
 * @param TInput The type of input the node receives from the graph.
 * @param TOutput The type of output the node produces for the graph.
 * @param instruction The base instruction for prompt construction.
 * @param inputField The key in Example.data that provides this node's input.
 * @param outputField The key in Example.data that provides this node's expected output.
 * @param promptFn The prompt function that handles prompt construction, LLM call, and response parsing.
 * @param name Explicit node name. If null, derived from the delegated property name.
 * @param description Optional description for MIPRO program description.
 * @return An [OptimizableNodeDelegate] for use with Kotlin property delegation (`by`).
 */
public inline fun <reified TInput, reified TOutput> AIAgentSubgraphBuilderBase<*, *>.optimizableNode(
    instruction: String,
    inputField: String,
    outputField: String,
    noinline promptFn: OptimizablePromptFn<TInput, TOutput>,
    name: String? = null,
    description: String? = null,
): OptimizableNodeDelegate<TInput, TOutput> {
    val delegateRef = object { lateinit var delegate: OptimizableNodeDelegate<TInput, TOutput> }

    val executeImpl: suspend AIAgentGraphContextBase.(TInput) -> TOutput = { input ->
        val nodeName = delegateRef.delegate.resolvedName
            ?: error("OptimizableNode name not resolved — graph not built yet?")
        val effectiveInstruction = getNodeInstruction(nodeName, instruction)
        val demos = getNodeDemonstrations<TInput, TOutput>(nodeName, emptyList())

        promptFn.invoke(this, effectiveInstruction, demos, input)
    }

    return OptimizableNodeDelegate(
        name = name,
        inputField = inputField,
        outputField = outputField,
        instruction = instruction,
        description = description,
        inputType = typeOf<TInput>(),
        outputType = typeOf<TOutput>(),
        executeImpl = executeImpl,
    ).also { delegateRef.delegate = it }
}
