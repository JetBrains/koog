@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ActiveProperty
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * An API of the [AIAgentLLMWriteSession]
 *
 * @property environment The agent environment that provides the session with tool execution
 * and error handling capabilities.
 * @property toolRegistry The registry containing tools available for use within the session.
 * @property clock The clock used for message timestamps
 */
public interface AIAgentLLMWriteSessionAPI : AIAgentLLMSessionAPI {
    /**
     * Represents the current execution environment for the AI agent.
     *
     * This property provides access to the configuration, context, and resources
     * necessary for the AI agent's operation. The environment encapsulates all
     * relevant state and dependencies required for the agent to perform its tasks.
     *
     * Note that this API is intended for internal use only and may be subject
     * to changes or removal in future updates.
     */
    @InternalAgentsApi
    public val environment: AIAgentEnvironment

    /**
     * A registry that holds information about available tools within the system.
     * This registry is typically used for managing tool instances and their configurations.
     *
     * The `@InternalAgentsApi` annotation indicates that this property is intended for
     * internal use within the agents API and may be subject to changes without notice.
     *
     */
    @InternalAgentsApi
    public val toolRegistry: ToolRegistry

    /**
     * [Clock] instance used for adding timestamps on LLM responses and other agent events.
     */
    public val clock: Clock

    /**
     * Represents the prompt object used within the session. The prompt can be accessed or
     * modified only when the session is in an active state, as determined by the `isActive` predicate.
     *
     * This property uses the [ActiveProperty] delegate to enforce the validation of the session's
     * active state before any read or write operations.
     */
    override var prompt: Prompt

    /**
     * Represents a collection of tools that are available for the session.
     * The tools can be accessed or modified only if the session is in an active state.
     *
     * This property uses an [ActiveProperty] delegate to enforce the session's active state
     * as a prerequisite for accessing or mutating the tools list.
     *
     * The list contains tool descriptors, which define the tools' metadata, such as their
     * names, descriptions, and parameter requirements.
     */
    override var tools: List<ToolDescriptor>

    /**
     * Represents an override property `model` of type [LLModel].
     * This property is backed by an `ActiveProperty`, which ensures the property value is dynamically updated
     * based on the active state determined by the `isActive` parameter.
     *
     * This implementation allows for reactive behavior, ensuring that the `model` value is updated or resolved
     * only when the `isActive` condition changes.
     */
    override var model: LLModel

    /**
     * Represents the active response processor within the session.
     * The processor defines the post-processing of messages returned from the LLM.
     */
    override var responseProcessor: ResponseProcessor?

    /**
     * Finds a specific tool instance from the tool registry based on the provided tool type.
     *
     * @param tool the tool instance whose type is used to search for a corresponding tool in the registry
     * @return a SafeTool instance corresponding to the found tool in the registry
     * @throws IllegalArgumentException if a tool of the provided type is not found in the registry
     */
    public fun <TArgs, TResult> findTool(tool: Tool<TArgs, TResult>): SafeTool<TArgs, TResult>

    /**
     * Finds a tool of the specified class from the tool registry and wraps it in a SafeTool instance.
     *
     * @param toolClass the class of the tool to search for in the tool registry
     * @return a SafeTool instance wrapping the found tool
     * @throws IllegalArgumentException if no tool of the specified class is found in the registry
     */
    @Suppress("UNCHECKED_CAST")
    public fun <TArgs, TResult> findTool(toolClass: KClass<out Tool<TArgs, TResult>>): SafeTool<TArgs, TResult>

    /**
     * Appends messages to the current prompt by applying modifications defined in the provided block.
     * The modifications are applied using a `PromptBuilder` instance, allowing for
     * customization of the prompt's content, structure, and associated messages.
     *
     * @param body A lambda with a receiver of type `PromptBuilder` that defines
     *             the modifications to be applied to the current prompt.
     */
    public fun appendPrompt(body: PromptBuilder.() -> Unit)

    /**
     * Updates the current prompt by applying modifications defined in the provided block.
     * The modifications are applied using a `PromptBuilder` instance, allowing for
     * customization of the prompt's content, structure, and associated messages.
     *
     * @param body A lambda with a receiver of type `PromptBuilder` that defines
     *             the modifications to be applied to the current prompt.
     */
    @Deprecated("Use `appendPrompt` instead", ReplaceWith("appendPrompt(body)"))
    public fun updatePrompt(body: PromptBuilder.() -> Unit)

    /**
     * Rewrites the current prompt by applying a transformation function.
     *
     * @param body A lambda function that receives the current prompt and returns a modified prompt.
     */
    public fun rewritePrompt(body: (prompt: Prompt) -> Prompt)

    /**
     * Updates the underlying model in the current prompt with the specified new model.
     *
     * @param newModel The new LLModel to replace the existing model in the prompt.
     */
    public fun changeModel(newModel: LLModel)

    /**
     * Updates the language model's parameters used in the current session prompt.
     *
     * @param newParams The new set of LLMParams to replace the existing parameters in the prompt.
     */
    public fun changeLLMParams(newParams: LLMParams): Unit

    /**
     * Sends a request to the language model without utilizing any tools, returns multiple responses,
     * and updates the prompt with the received messages.
     *
     * @return A list of response messages from the language model.
     */
    override suspend fun requestLLMMultipleWithoutTools(): List<Message.Response>

    /**
     * Sends a request to the Language Model (LLM) without including any tools, processes the response,
     * and updates the prompt with the returned message.
     *
     * LLM might answer only with a textual assistant message.
     *
     * @return the response from the LLM after processing the request, as a [Message.Response].
     */
    override suspend fun requestLLMWithoutTools(): Message.Response

    /**
     * Requests a response from the Language Learning Model (LLM) while also processing
     * the response by updating the current prompt with the received message.
     *
     * @return The response received from the Language Learning Model (LLM).
     */
    override suspend fun requestLLMOnlyCallingTools(): Message.Response

    /**
     * Requests a response from the Language Model (LLM) enforcing tool usage (`ToolChoice.Required`),
     * validates the session, and processes all returned messages (e.g. thinking + tool call).
     *
     * Crucially, this method appends **all** received messages to the prompt history to preserve context.
     *
     * @return A list of responses received from the Language Model (LLM).
     */
    override suspend fun requestLLMMultipleOnlyCallingTools(): List<Message.Response>

    /**
     * Requests an LLM (Large Language Model) to forcefully utilize a specific tool during its operation.
     *
     * @param tool A descriptor object representing the tool to be enforced for use by the LLM.
     * @return A response message received from the LLM after executing the enforced tool request.
     */
    override suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response

    /**
     * Requests the execution of a single specified tool, enforcing its use,
     * and updates the prompt based on the generated response.
     *
     * @param tool The tool that will be enforced and executed. It contains the input and output types.
     * @return The response generated after executing the provided tool.
     */
    override suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response

    /**
     * Makes an asynchronous request to a Large Language Model (LLM) and updates the current prompt
     * with the response received from the LLM.
     *
     * @return A [Message.Response] object containing the response from the LLM.
     */
    override suspend fun requestLLM(): Message.Response

    /**
     * Requests multiple responses from the LLM and updates the prompt with the received responses.
     *
     * This method invokes the superclass implementation to fetch a list of LLM responses. Each
     * response is subsequently used to update the session's prompt. The prompt updating mechanism
     * allows stateful interactions with the LLM, maintaining context across multiple requests.
     *
     * @return A list of `Message.Response` containing the results from the LLM.
     */
    override suspend fun requestLLMMultiple(): List<Message.Response>

    /**
     * Sends a request to LLM and gets a structured response.
     *
     * @param config A configuration defining structures and behavior.
     */
    override suspend fun <T> requestLLMStructured(
        config: StructuredRequestConfig<T>,
    ): Result<StructuredResponse<T>>

    /**
     * Sends a request to LLM and gets a structured response.
     *
     * This is a simple version of the full `requestLLMStructured`. Unlike the full version, it does not require specifying
     * struct definitions and structured output modes manually. It attempts to find the best approach to provide a structured
     * output based on the defined [model] capabilities.
     *
     * @param serializer Serializer for the requested structure type.
     * @param examples Optional list of examples in case manual mode will be used. These examples might help the model to
     * understand the format better.
     * @param fixingParser Optional parser that handles malformed responses by using an auxiliary LLM to
     * intelligently fix parsing errors. When specified, parsing errors trigger additional
     * LLM calls with error context to attempt correction of the structure format.
     */
    override suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T>,
        fixingParser: StructureFixingParser?
    ): Result<StructuredResponse<T>>

    /**
     * Streams the result of a request to a language model.
     *
     * @param definition an optional parameter to define a structured data format. When provided, it will be used
     * in constructing the prompt for the language model request.
     * @return a flow of `StreamingFrame` objects that streams the responses from the language model.
     */
    public suspend fun requestLLMStreaming(definition: StructureDefinition? = null): Flow<StreamFrame>
}
