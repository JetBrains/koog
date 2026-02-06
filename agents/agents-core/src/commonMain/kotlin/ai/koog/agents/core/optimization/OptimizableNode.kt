package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.dsl.getNodeDemonstrations
import ai.koog.agents.core.optimization.dsl.getNodeInstruction
import kotlin.reflect.KProperty
import kotlin.reflect.typeOf

/**
 * A prompt function that constructs the LLM response from instruction, demonstrations, and input.
 *
 * When provided, this replaces the default prompt construction logic of [OptimizableNode].
 * The function receives the effective instruction (possibly overridden by optimization),
 * the current demonstrations, and the user input. It should construct and send the prompt
 * via [AIAgentGraphContextBase.llm] and return the string result.
 */
public typealias OptimizablePromptFn = suspend AIAgentGraphContextBase.(
    instruction: String,
    demos: List<Demonstration<String, String>>,
    input: String,
) -> String

/**
 * A node with built-in optimization support for MIPRO-style prompt optimization.
 *
 * Unlike a regular [AIAgentNode], an [OptimizableNode] declares which fields of an [Example][ai.koog.agents.core.optimization.core.Example]
 * map to its input and output. This allows optimizers like [LabeledFewShot][ai.koog.agents.core.optimization.optimizers.LabeledFewShot]
 * to create per-node [Demonstration]s with the correct field values.
 *
 * The node owns prompt construction: it builds a prompt from instruction + demonstrations + input
 * and sends it to the LLM via [AIAgentGraphContextBase.llm]. The default prompt format is:
 * ```
 * system(instruction)
 * user(demo1.input) / assistant(demo1.output)
 * user(demo2.input) / assistant(demo2.output)
 * ...
 * user(input)
 * ```
 *
 * This can be overridden by providing a custom [OptimizablePromptFn].
 *
 * Usage:
 * ```kotlin
 * val classify by optimizableNode(
 *     instruction = "Classify the sentiment of the text.",
 *     inputField = "text",
 *     outputField = "sentiment",
 * )
 * ```
 *
 * @property inputField The key in [Example.data][ai.koog.agents.core.optimization.core.Example.data]
 *  that provides this node's input.
 * @property outputField The key in [Example.data][ai.koog.agents.core.optimization.core.Example.data]
 *  that provides this node's expected output.
 */
public class OptimizableNode internal constructor(
    name: String,
    public val inputField: String,
    public val outputField: String,
    execute: suspend AIAgentGraphContextBase.(String) -> String,
    instruction: String,
    description: String? = null,
) : AIAgentNode<String, String>(
    name = name,
    inputType = typeOf<String>(),
    outputType = typeOf<String>(),
    execute = execute,
    instruction = instruction,
    demonstrations = emptyList(),
    description = description,
)

/**
 * Property delegate that creates an [OptimizableNode] with automatic prompt construction.
 *
 * The delegate builds the execute lambda internally. At execution time, it:
 * 1. Resolves the effective instruction (from [OptimizationConfig][ai.koog.agents.core.optimization.core.OptimizationConfig] context or default)
 * 2. Resolves demonstrations (from context or default)
 * 3. Constructs and sends the prompt via [AIAgentGraphContextBase.llm]
 * 4. Returns the LLM response content as a String
 *
 * The node name is resolved lazily — either from an explicit name or from the delegated property name.
 * This is safe because graph construction (which triggers [getValue]) always happens before execution.
 *
 * @param name Explicit node name, or null to derive from the property name.
 * @param inputField The Example data key for this node's input.
 * @param outputField The Example data key for this node's output.
 * @param instruction The base instruction for prompt construction.
 * @param description Optional description for MIPRO program description.
 * @param promptFn Optional custom prompt function. When null, uses the default prompt construction.
 */
public class OptimizableNodeDelegate(
    private val name: String?,
    public val inputField: String,
    public val outputField: String,
    private val instruction: String,
    private val description: String?,
    private val promptFn: OptimizablePromptFn?,
) {
    private var resolvedName: String? = name
    private var optimizableNode: OptimizableNode? = null

    private val executeImpl: suspend AIAgentGraphContextBase.(String) -> String = { input ->
        val nodeName = resolvedName ?: error("OptimizableNode name not resolved — graph not built yet?")
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

    /**
     * Creates (or returns cached) the [OptimizableNode], deriving the name from [property] if not explicit.
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentNodeBase<String, String> {
        if (optimizableNode == null) {
            resolvedName = name ?: property.name
            optimizableNode = OptimizableNode(
                name = resolvedName!!,
                inputField = inputField,
                outputField = outputField,
                execute = executeImpl,
                instruction = instruction,
                description = description,
            )
        }
        return optimizableNode!!
    }
}

/**
 * Creates an optimizable node with automatic prompt construction and LLM execution.
 *
 * This is the primary DSL for declaring nodes that participate in MIPRO-style optimization.
 * The node owns how the Koog [Prompt][ai.koog.prompt.dsl.Prompt] is constructed from instruction,
 * demonstrations, and input. The LLM call happens via [promptExecutor][AIAgentGraphContextBase.llm].
 *
 * Default behavior (no [promptFn]):
 * - Constructs: `system(instruction) + user/assistant demo pairs + user(input)`
 * - Calls `requestLLMWithoutTools()` and returns `content`
 *
 * Custom behavior (with [promptFn]):
 * - The provided function receives the effective instruction, demos, and input
 * - It is responsible for constructing the prompt and calling the LLM
 *
 * Example:
 * ```kotlin
 * val classify by optimizableNode(
 *     instruction = "Classify the sentiment.",
 *     inputField = "text",
 *     outputField = "sentiment",
 * )
 *
 * // With custom prompt construction:
 * val classify by optimizableNode(
 *     instruction = "Classify the sentiment.",
 *     inputField = "text",
 *     outputField = "sentiment",
 *     promptFn = { instruction, demos, input ->
 *         llm.writeSession {
 *             appendPrompt {
 *                 system("$instruction\nOutput JSON only.")
 *                 for (demo in demos) {
 *                     user(demo.input)
 *                     assistant(demo.output)
 *                 }
 *                 user(input)
 *             }
 *             requestLLMWithoutTools()
 *         }.content
 *     }
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
    promptFn: OptimizablePromptFn? = null,
): OptimizableNodeDelegate {
    return OptimizableNodeDelegate(
        name = name,
        inputField = inputField,
        outputField = outputField,
        instruction = instruction,
        description = description,
        promptFn = promptFn,
    )
}
