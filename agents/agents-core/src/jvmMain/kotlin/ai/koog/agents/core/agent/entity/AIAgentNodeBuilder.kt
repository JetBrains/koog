@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.ContextualAction
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
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.setupLLMAsAJudge
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import kotlin.random.Random
import kotlin.reflect.full.defaultType

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
        AIAgentNodeBuilderWithInput(name, clazz)
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
            clazz
        )
}

/**
 * A builder class for configuring and creating a typed compression history node within an AI agent graph.
 *
 * @param Input The type of the input data for the node.
 * @property name The name of the node to be created.
 * @property inputClass The Kotlin class type of the input data.
 * @property retrievalModel An optional large language model (LLM) used for retrieval purposes.
 * @property strategy The strategy for compressing historical data, which determines how history is managed.
 * @property preserveMemory A flag indicating whether to prioritize preserving memory during history compression.
 */
@JavaAPI
public class TypedCompressHistoryNodeBuilder<Input : Any>(
    private val name: String,
    private val inputClass: Class<Input>,
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
        TypedCompressHistoryNodeBuilder(name, inputClass, model, strategy, preserveMemory)

    /**
     * Sets the history compression strategy to be used for this builder.
     *
     * @param strategy The history compression strategy to apply.
     * @return A new instance of `TypedCompressHistoryNodeBuilder` with the specified compression strategy.
     */
    public fun compressionStrategy(strategy: HistoryCompressionStrategy): TypedCompressHistoryNodeBuilder<Input> =
        TypedCompressHistoryNodeBuilder(name, inputClass, retrievalModel, strategy, preserveMemory)

    /**
     * Sets whether memory preservation is enabled for the node being built.
     *
     * @param preserveMemory A boolean indicating whether memory should be preserved.
     * @return This builder instance with the updated memory preservation setting.
     */
    public fun preserveMemory(preserveMemory: Boolean): TypedCompressHistoryNodeBuilder<Input> =
        TypedCompressHistoryNodeBuilder(name, inputClass, retrievalModel, strategy, preserveMemory)

    /**
     * Builds and returns an instance of [AIAgentNodeBase] configured for compressing history
     * in the AI agent strategy graph. The resulting node is bound to the current configuration
     * parameters, including the retrieval model, compression strategy, and memory preservation settings.
     *
     * @return An [AIAgentNodeBase] instance responsible for compressing history based on the
     * specified inputs and configuration within the strategy graph.
     */
    public fun build(): AIAgentNodeBase<Input, Input> = AIAgentNode.builder(name)
        .withInput(inputClass)
        .withOutput(inputClass)
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
    private val inputClass: Class<Input>
) {
    /**
     * Specifies the output type for the [AIAgentNode] and returns a builder for creating a typed [AIAgentNode].
     *
     * @param clazz The class representing the output type of the node.
     * @return A builder for creating a typed [AIAgentNode] configured with the specified output type.
     */
    public fun <Output : Any> withOutput(clazz: Class<Output>): TypedAIAgentNodeBuilder<Input, Output> =
        TypedAIAgentNodeBuilder(name, inputClass, clazz)

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
        .withOutput(inputClass)
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
    ): AIAgentNodeBase<Input, List<*>> =
        this // TODO: @EugeneTheDev change to List<Message.Response> once type tokens are merged
            .withOutput(List::class.java)
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
            inputType = inputClass.kotlin.defaultType,
            outputType = CriticResult::class.defaultType // TODO: @EugeneTheDev change to type token with generic info!
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
        .withOutput(inputClass)
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
    private val inputClass: Class<Input>,
    private val outputClass: Class<Output>
) {
    /**
     * Creates and returns an instance of [AIAgentNode] that encapsulates the provided execution logic.
     *
     * This method binds a specified action to an AI agent node, enabling the node to process input of type [Input]
     * and generate output of type [Output] within the context of an [AIAgentGraphContextBase].
     *
     * @param nodeAction A lambda function that represents the processing logic. It takes two parameters:
     * - [Input]: The input data for the node.
     * - [AIAgentGraphContextBase]: The execution context in which the action is performed.
     * The function returns a result of type [Output].
     *
     * @return A new instance of [AIAgentNode] configured with the provided processing logic, ready for execution within an AI graph.
     */
    public fun withAction(nodeAction: ContextualAction<Input, Output>): AIAgentNode<Input, Output> {
        return AIAgentNode(
            name ?: "node-${Random.nextInt()}",
            inputClass.kotlin.defaultType,
            outputClass.kotlin.defaultType
        ) { input ->
            this.config.runOnStrategyDispatcher {
                nodeAction.execute(input, this)
            }
        }
    }

    internal fun executeOnLLMDispatcher(
        asyncAction: suspend AIAgentGraphContextBase.(Input) -> Output
    ): AIAgentNode<Input, Output> = withAction { input, ctx ->
        ctx.config.runOnLLMDispatcher {
            ctx.asyncAction(input)
        }
    }

    @OptIn(InternalAgentsApi::class)
    internal fun executeOnStrategyDispatcher(
        asyncAction: suspend AIAgentGraphContextBase.(Input) -> Output
    ): AIAgentNode<Input, Output> = withAction { input, ctx ->
        ctx.config.runOnStrategyDispatcher {
            ctx.asyncAction(input)
        }
    }
}
