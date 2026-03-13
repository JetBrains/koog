@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.appendPromptImpl
import ai.koog.agents.core.dsl.extension.llmCompressHistoryImpl
import ai.koog.agents.core.dsl.extension.requestStreamingAndSendResultsImpl
import ai.koog.agents.core.dsl.extension.setStructuredOutputImpl
import ai.koog.agents.core.utils.runOnLLMDispatcher
import ai.koog.agents.core.utils.runOnStrategyDispatcher
import ai.koog.agents.core.utils.submitToMainDispatcher
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.setupLLMAsAJudge
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.serialization.TypeToken
import ai.koog.serialization.typeToken
import io.ktor.utils.io.core.Output
import kotlin.random.Random

/**
 * A Java builder class for creating [AIAgentNode] with a specified name.
 * This allows the configuration of the node's input type.
 *
 * @constructor Initializes the builder with the specified name.
 * @param name The name of the [AIAgentNode], or null if unspecified.
 */
@JavaAPI
public class AIAgentNodeBuilder(
    private val name: String?,
) {
    /**
     * Specifies the input type for building an [AIAgentNode].
     *
     * @param clazz the `Class` instance representing the input type.
     * @return an instance of `AIAgentNodeBuilderWithInput` configured with the specified input type.
     */
    public fun <Input : Any> withInput(clazz: Class<Input>): AIAgentNodeBuilderWithInput<Input> =
        AIAgentNodeBuilderWithInput(name, TypeToken.of(clazz))

    /**
     * Configures the builder to use the specified input type for constructing an [AIAgentNode].
     *
     * @param typeToken a [TypeToken] representing the input type.
     * @return an [AIAgentNodeBuilderWithInput] instance configured with the specified input type.
     */
    public fun <Input : Any> withInput(typeToken: TypeToken): AIAgentNodeBuilderWithInput<Input> =
        AIAgentNodeBuilderWithInput(name, typeToken)
}

/**
 * Builder class for creating a compress history node in an AI agent graph strategy.
 *
 * @param name The name of the node being created.
 */
@JavaAPI
public class CompressHistoryNodeBuilder(
    private val name: String,
) {
    /**
     * Configures the current `CompressHistoryNodeBuilder` with a specific input type,
     * returning a new `TypedCompressHistoryNodeBuilder` specialized for the provided type.
     *
     * @param Input The type of input to be associated with the resulting `TypedCompressHistoryNodeBuilder`.
     *              It must be a non-nullable type.
     * @param clazz The `Class` object representing the input type to associate with the builder.
     * @return A new instance of `TypedCompressHistoryNodeBuilder` configured with the specified input type.
     */
    public fun <Input : Any> withInput(clazz: Class<Input>): TypedCompressHistoryNodeBuilder<Input> =
        TypedCompressHistoryNodeBuilder(
            name,
            TypeToken.of(clazz)
        )

    /**
     * Configures the current `CompressHistoryNodeBuilder` with a specific input type using a `TypeToken`,
     * returning a new `TypedCompressHistoryNodeBuilder` specialized for the provided type.
     *
     * @param Input The type of input to be associated with the resulting `TypedCompressHistoryNodeBuilder`.
     *              It must be a non-nullable type.
     * @param typeToken The `TypeToken` representing the input type to associate with the builder.
     * @return A new instance of `TypedCompressHistoryNodeBuilder` configured with the specified input type.
     */
    public fun <Input : Any> withInput(typeToken: TypeToken): TypedCompressHistoryNodeBuilder<Input> =
        TypedCompressHistoryNodeBuilder(
            name,
            typeToken
        )
}

/**
 * A builder class for configuring and creating a typed compression history node within an AI agent graph.
 *
 * @param Input The type of the input data for the node.
 * @property name The name of the node to be created.
 * @property inputTypeToken Type token of the input data.
 * @property retrievalModel An optional large language model (LLM) used for retrieval purposes.
 * @property strategy The strategy for compressing historical data, which determines how history is managed.
 * @property preserveMemory A flag indicating whether to prioritize preserving memory during history compression.
 */
@JavaAPI
public class TypedCompressHistoryNodeBuilder<Input : Any>(
    private val name: String,
    private val inputTypeToken: TypeToken,
    private val retrievalModel: LLModel? = null,
    private val strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    private val preserveMemory: Boolean = true,
) {

    /**
     * Configures the node builder with a specific retrieval model.
     *
     * @param model The retrieval model to be used in the configured node. An instance of [LLModel] representing the model to apply.
     * @return A new instance of [TypedCompressHistoryNodeBuilder] with the specified retrieval model configured.
     */
    public fun withRetrievalModel(model: LLModel): TypedCompressHistoryNodeBuilder<Input> =
        TypedCompressHistoryNodeBuilder(name, inputTypeToken, model, strategy, preserveMemory)

    /**
     * Sets the history compression strategy to be used for this builder.
     *
     * @param strategy The history compression strategy to apply.
     * @return A new instance of `TypedCompressHistoryNodeBuilder` with the specified compression strategy.
     */
    public fun compressionStrategy(strategy: HistoryCompressionStrategy): TypedCompressHistoryNodeBuilder<Input> =
        TypedCompressHistoryNodeBuilder(name, inputTypeToken, retrievalModel, strategy, preserveMemory)

    /**
     * Sets whether memory preservation is enabled for the node being built.
     *
     * @param preserveMemory A boolean indicating whether memory should be preserved.
     * @return This builder instance with the updated memory preservation setting.
     */
    public fun preserveMemory(preserveMemory: Boolean): TypedCompressHistoryNodeBuilder<Input> =
        TypedCompressHistoryNodeBuilder(name, inputTypeToken, retrievalModel, strategy, preserveMemory)

    /**
     * Builds and returns an instance of [AIAgentNodeBase] configured for compressing history
     * in the AI agent strategy graph. The resulting node is bound to the current configuration
     * parameters, including the retrieval model, compression strategy, and memory preservation settings.
     *
     * @return An [AIAgentNodeBase] instance responsible for compressing history based on the
     * specified inputs and configuration within the strategy graph.
     */
    public fun build(): AIAgentNode<Input, Input> = AIAgentNode.builder(name)
        .withInput<Input>(inputTypeToken)
        .withOutput<Input>(inputTypeToken)
        .executeOnLLMDispatcher { input ->
            llmCompressHistoryImpl(input, retrievalModel, strategy, preserveMemory)
        }
}

/**
 * A Java builder class for creating [AIAgentNode] with a specified input type.
 *
 * @param Input The type of input data the [AIAgentNode] will process.
 * @property name An optional name for the agent node.
 * @property inputClass The class representation of the input type.
 */
@JavaAPI
public class AIAgentNodeBuilderWithInput<Input : Any>(
    private val name: String?,
    private val inputTypeToken: TypeToken
) {

    /**
     * Specifies the output type for the [AIAgentNode] and returns a builder for creating a typed [AIAgentNode].
     *
     * @param clazz The class representing the output type of the node.
     * @return A builder for creating a typed [AIAgentNode] configured with the specified output type.
     */
    public fun <Output : Any> withOutput(clazz: Class<Output>): TypedAIAgentNodeBuilder<Input, Output> =
        TypedAIAgentNodeBuilder(name, inputTypeToken, TypeToken.of(clazz))

    /**
     * Specifies the output type for the AI agent node and returns a builder for creating a typed AI agent node.
     *
     * The output type is identified using the provided [TypeToken], which allows handling generic types
     * and preserves type information during runtime.
     *
     * @param typeToken The [TypeToken] that represents the output type of the node.
     * @return A [TypedAIAgentNodeBuilder] configured with the specified input and output types,
     *         enabling further customization and creation of the typed AI agent node.
     */
    public fun <Output : Any> withOutput(typeToken: TypeToken): TypedAIAgentNodeBuilder<Input, Output> =
        TypedAIAgentNodeBuilder(name, inputTypeToken, typeToken)

    /**
     * Appends a prompt to the AI agent node configuration.
     *
     * The prompt is constructed using the provided `body` lambda, which operates on a `PromptBuilder`.
     * Optionally, a `name` can be provided to identify the prompt configuration node.
     *
     * @param name An optional name to identify the configuration node. Defaults to `null` if not specified.
     * @param body A lambda function that defines the prompt using the `PromptBuilder`.
     * @return An instance of `AIAgentNodeBase` configured with the specified prompt.
     */
    public fun appendPrompt(
        promptUpdate: PromptBuilderAction
    ): AIAgentNodeBase<Input, Input> = this
        .withOutput<Input>(inputTypeToken)
        .executeOnLLMDispatcher { input ->
            appendPromptImpl(input) {
                promptUpdate.build(this)
            }
        }

    /**
     * Represents an action that defines how a [PromptBuilder] is configured.
     *
     * This functional interface is primarily used in the context of building prompt-related configurations
     * for AI agent nodes. Implementations of this interface customize a [PromptBuilder] instance, which
     * facilitates the creation of structured or dynamic prompts.
     *
     * The interface is annotated with [JavaAPI], indicating it is designed to support interoperability
     * with Java code and follows conventions favorable for Java environments.
     */
    @JavaAPI
    public fun interface PromptBuilderAction {
        /**
         * Executes the provided action on the given PromptBuilder instance.
         *
         * @param promptBuilder The PromptBuilder instance to be configured or modified.
         */
        public fun build(promptBuilder: PromptBuilder)
    }

    /**
     * Sends a streaming request to the Large Language Model (LLM) and processes the results, optionally using
     * a specified structure definition for content customization.
     *
     * @param structureDefinition An optional [StructureDefinition] instance that defines the structure of
     * textual content for the LLM request. If `null`, the default behavior is used without structured customization.
     * @return An instance of [AIAgentNodeBase] with the input type [Input] and output type as a list of unspecified elements.
     */
    public fun llmRequestStreamingAndSendResults(
        structureDefinition: StructureDefinition? = null
    ): AIAgentNodeBase<Input, List<Message.Response>> =
        this
            .withOutput<List<Message.Response>>(typeToken<List<Message.Response>>())
            .executeOnLLMDispatcher { input ->
                requestStreamingAndSendResultsImpl(structureDefinition)
            }

    /**
     * Configures an AI agent node to evaluate and critique input data as a simulated "judge" using a specified task
     * and an optional Large Language Model (LLM).
     *
     * @param task The task or criteria used by the AI agent to evaluate input data.
     * @param llmModel An optional instance of [LLModel] representing the Large Language Model to be used. Defaults to `null`.
     * @return An instance of [AIAgentNodeBase] configured to process input of type [Input] and generate output of type [CriticResult].
     */
    @JvmOverloads
    public fun llmAsAJudge(
        task: String,
        llmModel: LLModel? = null
    ): AIAgentNodeBase<Input, CriticResult<Input>> {
        val node by node<Input, CriticResult<Input>>(
            inputType = inputTypeToken,
            outputType = typeToken(CriticResult::class, listOf(inputTypeToken))
        ) { input ->
            setupLLMAsAJudge(task, llmModel, input)
        }

        return node
    }

    /**
     * Configures the node to produce a structured output based on the specified configuration.
     *
     * This method allows setting up structured output behavior for an AI agent node by defining
     * how content should be structured when requests are processed. The structure is determined
     * by the specified `StructuredRequestConfig`, which provides options for different providers
     * and fallback behaviors.
     *
     * @param config The configuration specifying how structured output should be handled, including
     *               provider-specific definitions and default fallback options.
     * @return An instance of `AIAgentNodeBase` with the input type [Input] and output type [Input],
     *         updated with the configured structured output setup.
     */
    @JvmOverloads
    public fun <T : Any> setStructuredOutput(
        config: StructuredRequestConfig<T>,
    ): AIAgentNodeBase<Input, Input> = this
        .withOutput<Input>(inputTypeToken)
        .executeOnStrategyDispatcher { message ->
            setStructuredOutputImpl(config, message)
        }
}

/**
 * A Java builder class for creating instances of `AIAgentNode` with strongly typed input and output data.
 *
 * @param Input The type of the input data the node will process.
 * @param Output The type of the output data the node will produce.
 * @property name The name of the node, used for identification and debugging purposes.
 * @property inputClass The class representing the type of the input data.
 * @property outputClass The class representing the type of the output data.
 */
@JavaAPI
public class TypedAIAgentNodeBuilder<Input : Any, Output : Any>(
    private val name: String?,
    private val inputTypeToken: TypeToken,
    private val outputTypeToken: TypeToken
) {
    /**
     * Assigns a contextual action to the node being built, specifying the logic that should be executed
     * when the node processes an input within the AI agent graph context.
     *
     * @param nodeAction The [ContextualAction] to execute. This defines how the node processes input
     *                   and produces the corresponding output within the provided [AIAgentGraphContextBase].
     * @return A new instance of `DefinedAIAgentNodeBuilder` configured with the specified contextual action.
     */
    public fun withAction(nodeAction: ContextualAction<Input, Output>): DefinedAIAgentNodeBuilder<Input, Output> =
        DefinedAIAgentNodeBuilder(
            name,
            inputTypeToken,
            outputTypeToken,
            nodeAction
        )

    internal fun executeOnLLMDispatcher(
        asyncAction: suspend AIAgentGraphContextBase.(Input) -> Output
    ): AIAgentNode<Input, Output> = withAction { input, ctx ->
        ctx.config.runOnLLMDispatcher {
            ctx.asyncAction(input)
        }
    }.build()

    @OptIn(InternalAgentsApi::class)
    internal fun executeOnStrategyDispatcher(
        asyncAction: suspend AIAgentGraphContextBase.(Input) -> Output
    ): AIAgentNode<Input, Output> = withAction { input, ctx ->
        ctx.config.runOnStrategyDispatcher {
            ctx.asyncAction(input)
        }
    }.build()
}

/**
 * A builder class responsible for constructing instances of [AIAgentNode] with specific input and output types,
 * a unique node name, and a defined contextual action to be executed within an AI agent graph context.
 *
 * @param Input The type of the input data processed by the constructed `AIAgentNode`.
 * @param Output The type of the output data produced by the constructed `AIAgentNode`.
 * @constructor Initializes a new instance of the builder with the specified node name, input/output type tokens,
 *              and the contextual action to execute in the node.
 * @property name An optional name for the `AIAgentNode`. If not specified, a default name is generated.
 * @property inputTypeToken A `TypeToken` representing the input type.
 * @property outputTypeToken A `TypeToken` representing the output type.
 * @property nodeAction A `ContextualAction` defining the action to execute within the AI agent graph context.
 */
@JavaAPI
public class DefinedAIAgentNodeBuilder<Input : Any, Output : Any>(
    private val name: String?,
    private val inputTypeToken: TypeToken,
    private val outputTypeToken: TypeToken,
    private val nodeAction: ContextualAction<Input, Output>
) {
    /**
     * Builds and returns an instance of `AIAgentNode` configured with the specified parameters.
     * The node will execute the provided `ContextualAction` with the input data when invoked.
     *
     * @return A fully configured instance of `AIAgentNode` parameterized with `Input` and `Output` types.
     */
    public fun build(): AIAgentNode<Input, Output> {
        return AIAgentNode(
            name ?: "node-${Random.nextInt()}",
            inputTypeToken,
            outputTypeToken
        ) { input ->
            this.config.submitToMainDispatcher {
                nodeAction.execute(input, this)
            }
        }
    }
}
