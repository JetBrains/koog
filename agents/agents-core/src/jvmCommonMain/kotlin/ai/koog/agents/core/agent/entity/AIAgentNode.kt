@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.ModeratedMessage
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeExecuteToolsAndGetResults
import ai.koog.agents.core.dsl.extension.nodeLLMModerateMessage
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestForceOneTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequestOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.nodeLLMRequestWithoutTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.requestStreamingImpl
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.ext.llm.choice.ChoiceSelectionStrategy
import ai.koog.agents.ext.llm.choice.nodeSelectLLMChoice
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.serialization.TypeToken
import ai.koog.serialization.typeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.jdk9.asFlow
import kotlinx.coroutines.jdk9.asPublisher
import java.util.concurrent.Flow.Publisher
import kotlin.random.Random

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
    inputType: TypeToken,
    outputType: TypeToken,
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
         * @return A new `AIAgentNodeBuilder` instance.
         */
        @JavaAPI
        @JvmStatic
        @JvmOverloads
        public fun builder(name: String? = null): AIAgentNodeBuilder = AIAgentNodeBuilder(name)

        /**
         * A node that sends a user message and requests a response from the LLM.
         *
         * @param name Optional node name, defaults to delegate's property name.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun llmRequest(
            name: String? = null,
        ): AIAgentNodeBase<Message.User, Message.Assistant> {
            val node by nodeLLMRequest(name)
            return node
        }

        /**
         * A node that sends a user message and requests a response from the LLM,
         * where only tool calls are allowed.
         *
         * @param name Optional node name, defaults to delegate's property name.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun llmRequestOnlyCallingTools(
            name: String? = null,
        ): AIAgentNodeBase<Message.User, Message.Assistant> {
            val node by nodeLLMRequestOnlyCallingTools(name)
            return node
        }

        /**
         * A node that sends a user message and requests a response from the LLM
         * without allowing tool calls.
         *
         * @param name Optional node name, defaults to delegate's property name.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun llmRequestWithoutTools(
            name: String? = null,
        ): AIAgentNodeBase<Message.User, Message.Assistant> {
            val node by nodeLLMRequestWithoutTools(name)
            return node
        }

        /**
         * A node that sends a user message and requests a response from the LLM,
         * forcing the LLM to call the specified tool.
         *
         * @param name Optional node name, defaults to delegate's property name.
         * @param tool The [ToolDescriptor] of the tool to force the LLM to use.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun llmRequestForceOneTool(
            name: String? = null,
            tool: ToolDescriptor,
        ): AIAgentNodeBase<Message.User, Message.Assistant> {
            val node by nodeLLMRequestForceOneTool(name, tool)
            return node
        }

        /**
         * A node that sends a user message and requests a response from the LLM,
         * forcing the LLM to call the specified tool.
         *
         * @param name Optional node name, defaults to delegate's property name.
         * @param tool The [Tool] to force the LLM to use.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun llmRequestForceOneTool(
            name: String? = null,
            tool: Tool<*, *>,
        ): AIAgentNodeBase<Message.User, Message.Assistant> {
            val node by nodeLLMRequestForceOneTool(name, tool.descriptor)
            return node
        }

        /**
         * A node that moderates a message using the LLM.
         *
         * @param name Optional node name, defaults to delegate's property name.
         * @param moderatingModel Optional [LLModel] to use for moderation. If null, uses the agent's current model.
         * @param includeCurrentPrompt If true, includes the current prompt in the moderation request.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun llmModerateMessage(
            name: String? = null,
            moderatingModel: LLModel?,
            includeCurrentPrompt: Boolean,
        ): AIAgentNodeBase<Message, ModeratedMessage> {
            val node by nodeLLMModerateMessage(name, moderatingModel, includeCurrentPrompt)
            return node
        }

        /**
         * Creates a node for streaming responses from LLM and handling the incoming stream data.
         * This overload transforms the stream via a [Publisher] for Java interoperability.
         *
         * @param transformStreamData A function that processes the incoming stream of `StreamFrame` objects and transforms them into a publisher.
         * @param outputClass The class type of the transformed output data.
         * @param structureDefinition An optional definition of structured data.
         * @param name An optional name for the node being created.
         * @return An instance of `AIAgentNodeBase` with a message input and a publisher output.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun <T : Any> llmRequestStreaming(
            transformStreamData: (Publisher<StreamFrame>) -> Publisher<T>,
            outputClass: Class<T>,
            structureDefinition: StructureDefinition? = null,
            name: String? = null,
        ): AIAgentNodeBase<Message.User, Publisher<T>> =
            builder(name)
                .withInput(Message.User::class.java)
                .withOutput<Publisher<T>>(typeToken(Publisher::class, listOf(TypeToken.of(outputClass))))
                .executeOnLLMDispatcher { input ->
                    requestStreamingImpl(input, structureDefinition) { streamFrameFlow ->
                        transformStreamData(streamFrameFlow.asPublisher()).asFlow()
                    }.asPublisher()
                }

        /**
         * A node that sends a user message and requests a streaming response from the LLM.
         *
         * @param name Optional node name, defaults to delegate's property name.
         * @param structureDefinition Optional structure definition to customize the streaming response.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun llmRequestStreaming(
            name: String? = null,
            structureDefinition: StructureDefinition?,
        ): AIAgentNodeBase<Message.User, Flow<StreamFrame>> {
            val node by nodeLLMRequestStreaming(name, structureDefinition)
            return node
        }

        /**
         * A node that sends a user message and requests a structured response from the LLM.
         *
         * @param name Optional node name, defaults to delegate's property name.
         * @param config The [StructuredRequestConfig] defining the expected output type.
         * @param fixingParser Optional [StructureFixingParser] for correcting malformed responses.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun <T : Any> llmRequestStructured(
            name: String? = null,
            config: StructuredRequestConfig<T>,
            fixingParser: StructureFixingParser?,
        ): AIAgentNodeBase<Message.User, Result<StructuredResponse<T>>> {
            val node by nodeLLMRequestStructured(name, config, fixingParser)
            return node
        }

        /**
         * A node that executes all tool calls in the assistant message and appends results as a user message.
         *
         * @param name Optional node name, defaults to delegate's property name.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun executeTools(
            name: String? = null,
        ): AIAgentNodeBase<ToolCalls, Message.User> {
            val node by nodeExecuteTools(name)
            return node
        }

        /**
         * A node that executes all tool calls in the assistant message and appends results as a user message.
         *
         * @param name Optional node name, defaults to delegate's property name.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun executeToolsAndGetResults(
            name: String? = null,
        ): AIAgentNodeBase<ToolCalls, ReceivedToolResults> {
            val node by nodeExecuteToolsAndGetResults(name)
            return node
        }

        /**
         * A node that sends tool results as a message to the LLM.
         *
         * @param name Optional node name, defaults to delegate's property name.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun llmSendToolResults(
            name: String? = null,
        ): AIAgentNodeBase<ReceivedToolResults, Message.Assistant> {
            val node by nodeLLMSendToolResults(name)
            return node
        }

        /**
         * A node that chooses an LLM choice based on the given strategy.
         *
         * @param choiceSelectionStrategy The strategy used to choose an LLM choice.
         * @param name Optional name for the node.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun selectLLMChoice(
            choiceSelectionStrategy: ChoiceSelectionStrategy,
            name: String? = null,
        ): AIAgentNodeBase<LLMChoice, Message.Assistant> {
            val node by nodeSelectLLMChoice(choiceSelectionStrategy, name)
            return node
        }

        /**
         * Creates a new instance of `CompressHistoryNodeBuilder` with an optional custom name.
         *
         * @param name An optional string to specify a custom name for the `CompressHistoryNodeBuilder`.
         * @return A new instance of `CompressHistoryNodeBuilder` initialized with the provided or generated name.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun llmCompressHistory(name: String? = null): CompressHistoryNodeBuilder =
            CompressHistoryNodeBuilder(name ?: "compress-history-${Random.nextInt()}")
    }
}
