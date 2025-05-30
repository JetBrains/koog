package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.utils.ActiveProperty
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructuredData
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.executeStructured
import ai.koog.prompt.structure.executeStructuredOneShot

/**
 * Represents a session for an AI agent that interacts with an LLM (Language Learning Model).
 * The session manages prompt execution, structured outputs, and tools integration.
 *
 * This is a sealed class that provides common behavior and lifecycle management for derived types.
 * It ensures that operations are only performed while the session is active and allows proper cleanup upon closure.
 *
 * @property executor The executor responsible for executing prompts and handling LLM interactions.
 * @constructor Creates an instance of an [AIAgentLLMSession] with an executor, a list of tools, and a prompt.
 */
@OptIn(ExperimentalStdlibApi::class)
public sealed class AIAgentLLMSession(
    protected val executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    prompt: Prompt,
    model: LLModel,
    protected val config: AIAgentConfigBase,
) : AutoCloseable {
    /**
     * Represents the current prompt associated with the LLM session.
     * The prompt captures the input messages, model configuration, and parameters
     * used for interactions with the underlying language model.
     *
     * The property is managed using an active state validation mechanism, which ensures
     * that the prompt can only be accessed or modified when the session is active.
     *
     * Delegated by [ActiveProperty] to enforce session-based activity checks,
     * ensuring the property cannot be accessed when the [isActive] predicate evaluates to false.
     *
     * Typical usage includes providing input to LLM requests, such as:
     * - [requestLLMWithoutTools]
     * - [requestLLM]
     * - [requestLLMMultiple]
     * - [requestLLMStructured]
     * - [requestLLMStructuredOneShot]
     */
    public open val prompt: Prompt by ActiveProperty(prompt) { isActive }

    /**
     * Provides a list of tools based on the current active state.
     *
     * This property holds a collection of [ToolDescriptor] instances, which describe the tools available
     * for use in the AI agent session. The tools are dynamically determined and validated based on the
     * [isActive] state of the session. The property ensures that tools can only be accessed when the session
     * is active, leveraging the [ActiveProperty] delegate for state validation.
     *
     * Accessing this property when the session is inactive will raise an exception, ensuring consistency
     * and preventing misuse of tools outside a valid context.
     */
    public open val tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }


    /**
     * Represents the active language model used within the session.
     *
     * This property is backed by a delegate that ensures it can only be accessed
     * while the session is active, as determined by the [isActive] property.
     *
     * The model defines the language generation capabilities available for executing prompts
     * and tool interactions within the session's context.
     *
     * Usage of this property when the session is inactive will result in an exception.
     */
    public open val model: LLModel by ActiveProperty(model) { isActive }

    /**
     * A flag indicating whether the session is currently active.
     *
     * This variable is used to ensure that the session operations are only performed when the session is active.
     * Once the session is closed, this flag is set to `false` to prevent further usage.
     */
    protected var isActive: Boolean = true

    /**
     * Ensures that the session is active before allowing further operations.
     *
     * This method validates the state of the session using the [isActive] property
     * and throws an exception if the session has been closed. It is primarily intended
     * to prevent operations on an inactive or closed session, ensuring safe and valid usage.
     *
     * Throws:
     * - `IllegalStateException` if the session is not active.
     */
    protected fun validateSession() {
        check(isActive) { "Cannot use session after it was closed" }
    }

    protected fun preparePrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
        return config.missingToolsConversionStrategy.convertPrompt(prompt, tools)
    }

    protected suspend fun executeMultiple(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.execute(preparedPrompt, model, tools)
    }

    protected suspend fun executeSingle(prompt: Prompt, tools: List<ToolDescriptor>): Message.Response =
        executeMultiple(prompt, tools).first()


    public open suspend fun requestLLMWithoutTools(): Message.Response {
        validateSession()
        val promptWithDisabledTools = prompt.withUpdatedParams {
            /*
              If tools are empty, they will not be added by the LLM client to the requests,
              which means tool choice parameter cannot be used (it throws an exception without a tools parameter present).
              So instead set it to null in this case, which behaves the same (there are no tools to call, after all).
            */
            toolChoice = if (tools.isNotEmpty()) LLMParams.ToolChoice.None else null
        }
        return executeSingle(promptWithDisabledTools, tools)
    }

    public open suspend fun requestLLMOnlyCallingTools(): Message.Response {
        validateSession()
        val promptWithOnlyCallingTools = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Required
        }
        return executeSingle(promptWithOnlyCallingTools, tools)
    }

    public open suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response {
        validateSession()
        check(tools.contains(tool)) { "Unable to force call to tool `${tool.name}` because it is not defined" }
        val promptWithForcingOneTool = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Named(tool.name)
        }
        return executeSingle(promptWithForcingOneTool, tools)
    }

    public open suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response {
        return requestLLMForceOneTool(tool.descriptor)
    }

    /**
     * Sends a request to the underlying LLM and returns the first response.
     * This method ensures the session is active before executing the request.
     *
     * @return The first response message from the LLM after executing the request.
     */
    public open suspend fun requestLLM(): Message.Response {
        validateSession()
        return executeSingle(prompt, tools)
    }

    /**
     * Sends a request to the language model, potentially utilizing multiple tools,
     * and returns a list of responses from the model.
     *
     * Before executing the request, the session state is validated to ensure
     * it is active and usable.
     *
     * @return a list of responses from the language model
     */
    public open suspend fun requestLLMMultiple(): List<Message.Response> {
        validateSession()
        return executeMultiple(prompt, tools)
    }

    /**
     * Coerce LLM to provide a structured output.
     *
     * @see [executeStructured]
     */
    public open suspend fun <T> requestLLMStructured(
        structure: StructuredData<T>,
        retries: Int = 1,
        fixingModel: LLModel = OpenAIModels.Chat.GPT4o
    ): Result<StructuredResponse<T>> {
        validateSession()
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeStructured(preparedPrompt, model, structure, retries, fixingModel)
    }

    /**
     * Expect LLM to reply in a structured format and try to parse it.
     * For more robust version with model coercion and correction see [requestLLMStructured]
     *
     * @see [executeStructuredOneShot]
     */
    public open suspend fun <T> requestLLMStructuredOneShot(structure: StructuredData<T>): StructuredResponse<T> {
        validateSession()
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeStructuredOneShot(preparedPrompt, model, structure)
    }

    final override fun close() {
        isActive = false
    }
}
