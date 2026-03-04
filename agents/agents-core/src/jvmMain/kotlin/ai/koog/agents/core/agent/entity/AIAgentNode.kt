@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNode.Companion.llmRequestForceOneTool
import ai.koog.agents.core.agent.entity.AIAgentNode.Companion.llmRequestOnlyCallingTools
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.extension.ModeratedMessage
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleToolsAndSendResults
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMModerateMessage
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestForceOneTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultipleOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.nodeLLMSendMessageForceOneTool
import ai.koog.agents.core.dsl.extension.nodeLLMSendMessageOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResultsOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResultOnlyCallingTools
import ai.koog.agents.core.dsl.extension.requestStreamingImpl
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.ext.llm.choice.ChoiceSelectionStrategy
import ai.koog.agents.ext.llm.choice.nodeLLMSendResultsMultipleChoices
import ai.koog.agents.ext.llm.choice.nodeSelectLLMChoice
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
import kotlin.random.Random
import kotlin.reflect.KType

/**
 * Represents a simple implementation of an AI agent node, encapsulating a specific execution
 * logic that processes the input data and produces an output.
 *
 * @param TInput The type of input data this node processes.
 * @param TOutput The type of output data this node produces.
 * @property name The name of the node, used for identification and debugging.
 * @property execute A suspending function that defines the execution logic for the node. It
 * processes the provided input within the given execution context and produces an output.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual open class AIAgentNode<TInput, TOutput> internal actual constructor(
    name: String,
    inputType: KType,
    outputType: KType,
    execute: suspend AIAgentGraphContextBase.(input: TInput) -> TOutput,
) : SimpleAIAgentNodeImpl<TInput, TOutput>(
    name,
    inputType,
    outputType,
    execute
) {
    /**
     * Companion object for the AIAgentNode class.
     */
    public companion object {
        /**
         * Creates and returns a new instance of `AIAgentNodeBuilder`, used for constructing and configuring
         * instances of `AIAgentNode`.
         *
         * The builder provides a fluent interface for specifying properties and behaviors for the AI agent node,
         * simplifying the creation process.
         *
         * @return A new `AIAgentNodeBuilder` instance.
         */
        @JavaAPI
        @JvmStatic
        @JvmOverloads
        public fun builder(name: String? = null): AIAgentNodeBuilder = AIAgentNodeBuilder(name)

        /**
         * Creates an AI agent node for handling language model requests.
         *
         * @param name The optional name of the node. If null, the name will be automatically generated.
         * @param allowToolCalls Indicates whether the node is allowed to make tool calls during execution. Defaults to true.
         * @return An instance of [AIAgentNodeBase] configured to process language model requests with input of type [String] and output of type [Message.Response].
         */
        @JvmOverloads
        public fun llmRequest(
            allowToolCalls: Boolean = true,
            name: String? = null,
        ): AIAgentNodeBase<String, Message.Response> {
            val node by nodeLLMRequest(name, allowToolCalls)
            return node
        }

        /**
         * A utility method that performs no modifications or operations on its input
         * and simply returns it as output. Useful as a pass-through or identity transformation.
         *
         * @param clazz the `Class` instance representing the type of the input and output.
         * @param name an optional name for the node. If null, a default name is used.
         * @return an instance of [AIAgentNodeBase] configured to return the input of type [T] as the output without modification.
         */
        @JvmOverloads
        public fun <T : Any> doNothing(
            clazz: Class<T>,
            name: String? = null
        ): AIAgentNodeBase<T, T> = builder(name).withInput(clazz).withOutput(clazz).withAction { input, _ -> input }

        /**
         * Creates an AI agent node that processes language model requests while exclusively enabling tool calls during execution.
         *
         * @param name An optional name for the node. If null, the name will be automatically generated.
         * @return An instance of [AIAgentNodeBase] configured to handle language model requests with input of type [String]
         *         and output of type [Message.Response], where only tool calls are permitted.
         */
        @JvmOverloads
        public fun llmRequestOnlyCallingTools(
            name: String? = null
        ): AIAgentNodeBase<String, Message.Response> {
            val node by nodeLLMRequestOnlyCallingTools(name)
            return node
        }

        /**
         * Creates an AI agent node configured to handle language model requests that only involve tool calls.
         * This method is deprecated and replaced by [llmRequestOnlyCallingTools].
         *
         * @param name An optional name for the node. If null, a name will be automatically generated.
         * @return An instance of [AIAgentNodeBase] configured to process language model requests with input of type [String]
         *         and output of type [Message.Response].
         */
        @JvmOverloads
        @Deprecated("Use llmRequestOnlyCallingTools instead")
        public fun llmSendMessageOnlyCallingTools(
            name: String? = null
        ): AIAgentNodeBase<String, Message.Response> {
            val node by nodeLLMSendMessageOnlyCallingTools(name)
            return node
        }

        /**
         * Creates an AI agent node designed to handle language model requests where only tool calls
         * are executed, and multiple responses are returned.
         *
         * @param name An optional name for the node. If null, a default name will be generated automatically.
         * @return An instance of [AIAgentNodeBase] configured to process language model requests with
         *         input of type [String] and output of type [List<Message.Response>].
         */
        @JvmOverloads
        public fun llmRequestMultipleOnlyCallingTools(
            name: String? = null
        ): AIAgentNodeBase<String, List<Message.Response>> {
            val node by nodeLLMRequestMultipleOnlyCallingTools(name)
            return node
        }

        /**
         * Creates an AI agent node that processes a language model request while forcefully utilizing a specific tool.
         *
         * @param name The optional name of the node. If null, the name will be automatically generated.
         * @param tool A descriptor of the tool that must be utilized during the request execution.
         * @return An instance of [AIAgentNodeBase] configured to process the language model request with input of type [String] and output of type [Message.Response], ensuring the specified
         *  tool is used.
         */
        @JvmOverloads
        public fun llmRequestForceOneTool(
            tool: ToolDescriptor,
            name: String? = null
        ): AIAgentNodeBase<String, Message.Response> {
            val node by nodeLLMRequestForceOneTool(name, tool)
            return node
        }

        /**
         * Creates an AI agent node that forces the use of a specified tool during the handling of a language model request.
         *
         * @param name An optional name for the node. If null, a name will be automatically generated.
         * @param tool The descriptor of the tool that must be used during the node's execution.
         * @return An instance of [AIAgentNodeBase] configured to process language model requests with input of type [String]
         * and output of type [Message.Response].
         * @deprecated Use [llmRequestForceOneTool] instead, as it provides the same functionality with updated naming conventions.
         */
        @JvmOverloads
        @Deprecated("Use llmRequestForceOneTool instead")
        public fun llmSendMessageForceOneTool(
            tool: ToolDescriptor,
            name: String? = null
        ): AIAgentNodeBase<String, Message.Response> {
            val node by nodeLLMSendMessageForceOneTool(name, tool)
            return node
        }

        /**
         * Creates an AI agent node that forces the execution of a single specified tool during the handling
         * of a language model request.
         *
         * @param name An optional name for the node. If null, a default name will be generated.
         * @param tool The tool to be used during request execution. This tool is mandatory for the node's operation.
         * @return An instance of [AIAgentNodeBase] configured to process language model requests with input
         *         of type [String] and output of type [Message.Response].
         */
        @JvmOverloads
        public fun llmRequestForceOneTool(
            tool: Tool<*, *>,
            name: String? = null
        ): AIAgentNodeBase<String, Message.Response> {
            val node by nodeLLMRequestForceOneTool(name, tool)
            return node
        }

        /**
         * Creates an AI agent node for handling a language model request that forcibly uses a single tool.
         *
         * @param name An optional name for the node. If null, the name will be automatically generated.
         * @param tool The tool to be forcibly used during the request processing.
         * @return An instance of [AIAgentNodeBase] configured to process language model requests with input of type [String]
         * and output of type [Message.Response].
         * @throws IllegalStateException if the node could not be created due to invalid configurations.
         */
        @JvmOverloads
        @Deprecated("Use llmRequestForceOneTool instead")
        public fun llmSendMessageForceOneTool(
            tool: Tool<*, *>,
            name: String? = null
        ): AIAgentNodeBase<String, Message.Response> {
            val node by nodeLLMSendMessageForceOneTool(name, tool)
            return node
        }

        /**
         * Creates an AI agent node for moderating messages using a specified language model.
         *
         * @param name The optional name for the node. If null, the name will be generated automatically.
         * @param moderatingModel The language model to be used for moderation. If null, a default model will be used.
         * @param includeCurrentPrompt Indicates whether the current prompt should be included in the moderation process. Defaults to false.
         * @return An instance of [AIAgentNodeBase] configured to process input messages of type [Message] and produce moderated messages of type [ModeratedMessage].
         */
        @JvmOverloads
        public fun llmModerateMessage(
            moderatingModel: LLModel? = null,
            includeCurrentPrompt: Boolean = false,
            name: String? = null
        ): AIAgentNodeBase<Message, ModeratedMessage> {
            val node by nodeLLMModerateMessage(name, moderatingModel, includeCurrentPrompt)
            return node
        }

        /**
         * Performs a streaming request to an LLM (Language Model) with an optional name and structure definition,
         * allowing for transformation of the stream data.
         *
         * @param name An optional name for the streaming request. If null, no name will be associated with the request.
         * @param structureDefinition An optional structure definition to define the shape or format of the streaming data.
         *                            If null, no specific structure is assumed.
         * @param transformStreamData A function to transform the stream data. Takes a `Publisher` of `StreamFrame` as input
         *                            and returns a `Publisher` of the transformed data of type `T`.
         * @return An instance of `AIAgentNodeBase` with an input type of `String` and an output type of `Publisher` of any type.
         */
        @JvmOverloads
        public fun <T : Any> llmRequestStreaming(
            transformStreamData: (Publisher<StreamFrame>) -> Publisher<T>,
            structureDefinition: StructureDefinition? = null,
            name: String? = null
        ): AIAgentNodeBase<String, Publisher<*>> =
            builder(name) // TODO: @EugeneTheDev: change to Publisher<T> once type tokens are ready
                .withInput(String::class.java)
                .withOutput(Publisher::class.java)
                .executeOnLLMDispatcher { input ->
                    requestStreamingImpl(input, structureDefinition) { streamFrameFlow ->
                        transformStreamData(streamFrameFlow.asPublisher()).asFlow()
                    }.asPublisher()
                }

        /**
         * Creates an AI agent node configured for processing streaming requests to a language model.
         *
         * @param name An optional name for the node. If null, the name will be automatically generated.
         * @param structureDefinition An optional structure definition for customizing the content generated by the node.
         * @return An instance of [AIAgentNodeBase], configured to process language model requests with input of type [String]
         * and output as a stream of [StreamFrame].
         */
        @JvmOverloads
        public fun llmRequestStreaming(
            structureDefinition: StructureDefinition? = null,
            name: String? = null
        ): AIAgentNodeBase<String, Flow<StreamFrame>> {
            val node by nodeLLMRequestStreaming(name, structureDefinition)
            return node
        }

        /**
         * Creates an AI agent node that processes multiple responses from a language model request.
         *
         * @param name Optional name for the node. If null, an auto-generated name will be used.
         * @return An instance of [AIAgentNodeBase] configured to handle language model requests with input of type [String] and output of type [List] of [Message.Response].
         */
        @JvmOverloads
        public fun llmRequestMultiple(
            name: String? = null
        ): AIAgentNodeBase<String, List<Message.Response>> {
            val node by nodeLLMRequestMultiple(name)
            return node
        }

        /**
         * Executes a tool and returns an AI agent node configured for tool execution.
         *
         * @param name An optional name for the tool execution node. If null, a default name is generated.
         * @return An instance of [AIAgentNodeBase] that handles tool execution with input of type [Message.Tool.Call]
         *         and output of type [ReceivedToolResult].
         */
        @JvmOverloads
        public fun executeTool(
            name: String? = null
        ): AIAgentNodeBase<Message.Tool.Call, ReceivedToolResult> {
            val node by nodeExecuteTool(name)
            return node
        }

        /**
         * Creates an AI agent node for sending a single tool result to the language model.
         *
         * @param name An optional name for the node. If not specified, a default name is automatically assigned.
         * @return An instance of [AIAgentNodeBase] configured to process input of type [ReceivedToolResult]
         *         and produce output of type [Message.Response].
         */
        @JvmOverloads
        public fun llmSendToolResult(
            name: String? = null
        ): AIAgentNodeBase<ReceivedToolResult, Message.Response> {
            val node by nodeLLMSendToolResult(name)
            return node
        }

        /**
         * Creates an AI agent node that processes a list of tool execution results and generates a response,
         * limited to nodes that make tool calls during execution.
         *
         * @param name The optional name of the node. If null, the name will be automatically generated.
         * @return An instance of [AIAgentNodeBase] configured to process input of type [List<ReceivedToolResult>]
         *         and output a single [Message.Response].
         */
        @JvmOverloads
        public fun llmSendToolResultOnlyCallingTools(
            name: String? = null
        ): AIAgentNodeBase<List<ReceivedToolResult>, Message.Response> {
            val node by nodeLLMSendToolResultOnlyCallingTools(name)
            return node
        }

        /**
         * Executes multiple tools as part of an AI agent's processing node.
         *
         * @param name An optional name for the node. If not provided, a default name will be generated.
         * @param parallelTools A flag indicating whether the tools should be executed in parallel. Defaults to `false`.
         * @return A node configured to handle execution of a list of tools (`List<Message.Tool.Call>`)
         *         with the results being a list of received tool results (`List<ReceivedToolResult>`).
         */
        @JvmOverloads
        public fun executeMultipleTools(
            parallelTools: Boolean = false,
            name: String? = null
        ): AIAgentNodeBase<List<Message.Tool.Call>, List<ReceivedToolResult>> {
            val node by nodeExecuteMultipleTools(name, parallelTools)
            return node
        }

        /**
         * Executes multiple tools and sends their aggregated results as responses.
         *
         * @param name An optional name for the node. If null, a default name will be generated.
         * @param parallelTools Indicates whether the tools should be executed in parallel. Defaults to false.
         * @return An instance of [AIAgentNodeBase] that performs the execution of multiple tools with an input type of [List] of [Message.Tool.Call]
         *         and outputs a [List] of [Message.Response].
         */
        @JvmOverloads
        public fun executeMultipleToolsAndSendResults(
            parallelTools: Boolean = false,
            name: String? = null
        ): AIAgentNodeBase<List<Message.Tool.Call>, List<Message.Response>> {
            val node by nodeExecuteMultipleToolsAndSendResults(name, parallelTools)
            return node
        }

        /**
         * Creates an AI agent node for sending multiple tool results to a language model.
         *
         * @param name An optional name for the node. If null, a name will be automatically generated.
         * @return An instance of [AIAgentNodeBase] configured to process input of type [List] of [ReceivedToolResult]
         *         and generate output of type [List] of [Message.Response].
         */
        @JvmOverloads
        public fun llmSendMultipleToolResults(
            name: String? = null
        ): AIAgentNodeBase<List<ReceivedToolResult>, List<Message.Response>> {
            val node by nodeLLMSendMultipleToolResults(name)
            return node
        }

        /**
         * Creates an AI agent node designed to process multiple tool result responses while restricting interactions
         * to only the tools that were explicitly invoked. This is useful for controlled scenarios where tool response
         * management is confined to specific tools.
         *
         * @param name An optional name for the node. If null, the name will be automatically generated.
         * @return An instance of [AIAgentNodeBase] configured to handle a list of [ReceivedToolResult] as input and
         *         generate a list of [Message.Response] as output, ensuring only called tools are involved.
         */
        @JvmOverloads
        public fun llmSendMultipleToolResultsOnlyCallingTools(
            name: String? = null
        ): AIAgentNodeBase<List<ReceivedToolResult>, List<Message.Response>> {
            val node by nodeLLMSendMultipleToolResultsOnlyCallingTools(name)
            return node
        }

        /**
         * Sends a structured request to a language model and returns a corresponding response node.
         *
         * @param name An optional name for the request node. Defaults to `null`.
         * @param config The configuration for the structured request that defines the input and expected output types.
         * @param fixingParser An optional parser for fixing or refining the structure of the response. Defaults to `null`.
         * @return A node that represents the result of the structured language model request, containing a string input
         *         and a structured response of type `Result<StructuredResponse<T>>`.
         */
        @JvmOverloads
        public fun <T : Any> llmRequestStructured(
            config: StructuredRequestConfig<T>,
            fixingParser: StructureFixingParser? = null,
            name: String? = null
        ): AIAgentNodeBase<String, Result<StructuredResponse<T>>> {
            val node by nodeLLMRequestStructured(name, config, fixingParser)
            return node
        }


        /**
         * A node that sends multiple tool execution results to the LLM and gets multiple LLM choices.
         *
         * @param name Optional name for the node.
         */
        @JvmOverloads
        public fun lLMSendResultsMultipleChoices(
            name: String? = null
        ): AIAgentNodeBase<List<ReceivedToolResult>, List<LLMChoice>> {
            val node by nodeLLMSendResultsMultipleChoices(name)
            return node
        }

        /**
         * A node that chooses an LLM choice based on the given strategy.
         *
         * @param choiceSelectionStrategy The strategy used to choose an LLM choice.
         * @param name Optional name for the node.
         */
        @AIAgentBuilderDslMarker
        public fun selectLLMChoice(
            choiceSelectionStrategy: ChoiceSelectionStrategy,
            name: String? = null
        ): AIAgentNodeBase<List<LLMChoice>, LLMChoice> {
            val node by nodeSelectLLMChoice(choiceSelectionStrategy, name)
            return node
        }

        /**
         * Creates a new instance of `CompressHistoryNodeBuilder` with an optional custom name.
         * If no name is specified, a default name will be generated using the current node counter.
         *
         * @param name An optional string to specify a custom name for the `CompressHistoryNodeBuilder`.
         *             If null, a default name is generated in the format "compress-history-{counter}".
         * @return A new instance of `CompressHistoryNodeBuilder` initialized with the provided or generated name.
         */
        @JvmOverloads
        public fun llmCompressHistory(name: String? = null): CompressHistoryNodeBuilder =
            CompressHistoryNodeBuilder(name ?: "compress-history-${Random.nextInt()}")

        /**
         * Creates an AI agent node that performs a structured request using the given tool.
         *
         * @param name Optional name of the request. Can be used to identify or label the request.
         * @param tool The tool used to perform the structured request. It contains argument and result type information.
         * @param doAppendPrompt Indicates whether to append the prompt during execution. Defaults to true.
         * @return An AI agent node that is configured to use the specified tool and performs structured requests.
         */
        @JvmOverloads
        public fun <ToolArg, TResult> llmRequestStructured(
            tool: Tool<ToolArg, TResult>,
            doAppendPrompt: Boolean = true,
            name: String? = null
        ): AIAgentNodeBase<ToolArg, SafeTool.Result<TResult>> {
            // TODO: @EugeneTheDev: once type tokens are done
            return TODO("withInput(tool.argsToken).withOutput(tool.resultToken).executeOnStrategyDispatcher { toolArgs -> executeSingleToolImpl(tool, toolArgs, doAppendPrompt) }")
        }
    }
}
