package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.utils.ActiveProperty
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.LLMChoice
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

    /**
     * Sends a request to the language model without utilizing any tools and returns the response.
     *
     * This method validates the session state before proceeding with the operation. If tool usage
     * is disabled (i.e., the tools list is empty), the tool choice parameter will be set to null
     * to ensure compatibility with the underlying LLM client's behavior. It then executes the request
     * and retrieves the response from the LLM.
     *
     * @return The response message from the language model after executing the request, represented
     *         as a [Message.Response] instance.
     */
    public open suspend fun requestLLMWithoutTools(): Message.Response {
        validateSession()
        /*
            Not all LLM providers support tool list when tool choice is set to "none", so we are rewriting all tool messages to regular messages,
            for all requests without tools.
         */
        val promptWithDisabledTools = prompt
            .withUpdatedParams { toolChoice = null }
            .let { preparePrompt(it, emptyList()) }

        return executeSingle(promptWithDisabledTools, emptyList())
    }

    /**
     * Sends a request to the language model that enforces the usage of tools and retrieves the response.
     *
     * This method updates the session's prompt configuration to mark tool usage as required before
     * executing the request. Additionally, it ensures the session is active before proceeding.
     *
     * @return The response from the language model after executing the request with enforced tool usage.
     */
    public open suspend fun requestLLMOnlyCallingTools(): Message.Response {
        validateSession()
        val promptWithOnlyCallingTools = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Required
        }
        return executeSingle(promptWithOnlyCallingTools, tools)
    }

    /**
     * Sends a request to the language model while enforcing the use of a specific tool,
     * and returns the response.
     *
     * This method validates that the session is active and checks if the specified tool
     * exists within the session's set of available tools. It updates the prompt configuration
     * to enforce the selection of the specified tool before executing the request.
     *
     * @param tool The tool to be used for the request, represented by a [ToolDescriptor] instance.
     *             This parameter ensures that the language model utilizes the specified tool
     *             during the interaction.
     * @return The response from the language model as a [Message.Response] instance after
     *         processing the request with the enforced tool.
     */
    public open suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response {
        validateSession()
        check(tools.contains(tool)) { "Unable to force call to tool `${tool.name}` because it is not defined" }
        val promptWithForcingOneTool = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Named(tool.name)
        }
        return executeSingle(promptWithForcingOneTool, tools)
    }

    /**
     * Sends a request to the language model while enforcing the use of a specific tool, and returns the response.
     *
     * This method ensures the session is active and updates the prompt configuration to enforce the selection of the
     * specified tool before executing the request. It uses the provided tool as a focus for the language model to process
     * the interaction.
     *
     * @param tool The tool to be used for the request, represented as an instance of [Tool]. This parameter ensures
     *             the specified tool is utilized during the LLM interaction.
     * @return The response from the language model as a [Message.Response] instance after processing the request with the
     *         enforced tool.
     */
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
     * Sends a moderation request to the specified or default large language model (LLM) for content moderation.
     *
     * This method validates the session state before processing the request. It prepares the prompt
     * and uses the executor to perform the moderation check. A specific moderating model can be provided;
     * if not, the default session model will be used.
     *
     * @param moderatingModel An optional [LLModel] instance representing the model to be used for moderation.
     *                        If null, the default model configured for the session will be used.
     * @return A [ModerationResult] instance containing the details of the moderation analysis, including
     *         content classification and flagged categories.
     */
    public open suspend fun requestModeration(moderatingModel: LLModel? = null): ModerationResult {
        validateSession()
        val preparedPrompt = preparePrompt(prompt, emptyList())
        return executor.moderate(preparedPrompt, moderatingModel ?: model)
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
        val preparedPrompt = preparePrompt(prompt, tools = emptyList())
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
        val preparedPrompt = preparePrompt(prompt, tools = emptyList())
        return executor.executeStructuredOneShot(preparedPrompt, model, structure)
    }

    /**
     * Sends a request to the language model, potentially receiving multiple choices,
     * and returns a list of choices from the model.
     *
     * Before executing the request, the session state is validated to ensure
     * it is active and usable.
     *
     * @return a list of choices from the model
     */
    public open suspend fun requestLLMMultipleChoices(): List<LLMChoice> {
        validateSession()
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeMultipleChoices(preparedPrompt, model, tools)
    }

    final override fun close() {
        isActive = false
    }
}
