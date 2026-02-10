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
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass
import kotlin.time.Clock

/**
 * API of the [AIAgentLLMWriteSession]
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
    public fun changeLLMParams(newParams: LLMParams)

    /**
     * Streams the result of a request to a language model.
     *
     * @param definition an optional parameter to define a structured data format. When provided, it will be used
     * in constructing the prompt for the language model request.
     * @return a flow of `StreamingFrame` objects that streams the responses from the language model.
     */
    public suspend fun requestLLMStreaming(definition: StructureDefinition? = null): Flow<StreamFrame>
}
