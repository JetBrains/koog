package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.utils.ActiveProperty
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.executor.model.parseResponseToStructuredResponse
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.processor.ResponseProcessorConfig
import ai.koog.prompt.processor.executeProcessed
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Common base implementation for read-only LLM sessions shared across platform-specific actual classes.
 */
@OptIn(ExperimentalStdlibApi::class)
public abstract class AIAgentLLMReadSessionCommon internal constructor(
    private val executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    prompt: Prompt,
    model: LLModel,
    responseProcessor: ResponseProcessor?,
    /**
     * Config of the agent running the session.
     */
    public val config: AIAgentConfig,
    private var isActive: Boolean = true
) : AutoCloseable {
    /**
     * Represents the current prompt associated with the LLM session.
     * The prompt contains the input messages, model configuration, and parameters.
     */
    public val prompt: Prompt by ActiveProperty(prompt) { isActive }

    /**
     * Provides a list of available tools in the session.
     */
    public val tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }

    /**
     * Represents the active language model used within the session.
     */
    public val model: LLModel by ActiveProperty(model) { isActive }

    /**
     * Represents the active response processor within the session.
     */
    public val responseProcessor: ResponseProcessor? by ActiveProperty(responseProcessor) { isActive }

    protected fun validateSession() {
        check(isActive) { "Cannot use session after it was closed" }
    }

    protected fun preparePrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
        return config.missingToolsConversionStrategy.convertPrompt(prompt, tools)
    }

    /**
     * Executes a streaming request for the provided prompt and tools.
     */
    public fun executeStreaming(prompt: Prompt, tools: List<ToolDescriptor>): Flow<StreamFrame> {
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeStreaming(preparedPrompt, model, tools)
    }

    /**
     * Executes a request for the provided prompt and tools and returns all response messages.
     */
    public suspend fun executeMultiple(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeProcessed(
            prompt = preparedPrompt,
            model = model,
            tools = tools,
            processorConfig = responseProcessor?.let { ResponseProcessorConfig(it, config.serializer) }
        )
    }

    /**
     * Executes a request for the provided prompt and tools and returns the first response.
     */
    public suspend fun executeSingle(prompt: Prompt, tools: List<ToolDescriptor>): Message.Response =
        executeMultiple(prompt, tools).first()

    /**
     * Sends a request to the language model without utilizing any tools and returns multiple responses.
     *
     * @return A list of response messages from the language model.
     */
    public suspend fun requestLLMMultipleWithoutTools(): List<Message.Response> {
        validateSession()

        val promptWithDisabledTools = prompt
            .withUpdatedParams { toolChoice = null }
            .let { preparePrompt(it, emptyList()) }

        return executeMultiple(promptWithDisabledTools, emptyList())
    }

    /**
     * Sends a request to the language model without utilizing any tools and returns the response.
     *
     * @return The response message from the language model after executing the request.
     */
    public suspend fun requestLLMWithoutTools(): Message.Response {
        validateSession()
        val promptWithDisabledTools = prompt
            .withUpdatedParams { toolChoice = null }
            .let { preparePrompt(it, emptyList()) }

        return executeMultiple(promptWithDisabledTools, emptyList()).first { it !is Message.Reasoning }
    }

    /**
     * Sends a request to the language model that enforces the usage of tools and retrieves the response.
     *
     * @return The first tool call response if present, otherwise the first assistant response.
     */
    public suspend fun requestLLMOnlyCallingTools(): Message.Response {
        validateSession()
        val promptWithOnlyCallingTools = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Required
        }
        val responses = executeMultiple(promptWithOnlyCallingTools, tools)

        return responses.firstOrNull { it is Message.Tool.Call }
            ?: responses.first { it is Message.Assistant }
    }

    /**
     * Sends a request to the language model that enforces the usage of tools and retrieves all responses.
     *
     * @return A list of responses from the language model.
     */
    public suspend fun requestLLMMultipleOnlyCallingTools(): List<Message.Response> {
        validateSession()
        val promptWithOnlyCallingTools = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Required
        }
        return executeMultiple(promptWithOnlyCallingTools, tools)
    }

    /**
     * Sends a request to the language model while enforcing the use of a specific tool.
     *
     * @param tool The tool descriptor to force in the request.
     * @return The response from the language model.
     */
    public suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response {
        validateSession()
        check(tools.contains(tool)) { "Unable to force call to tool `${tool.name}` because it is not defined" }
        val promptWithForcingOneTool = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Named(tool.name)
        }
        val responses = executeMultiple(promptWithForcingOneTool, tools)

        return responses.firstOrNull { it is Message.Tool.Call }
            ?: responses.first { it is Message.Assistant }
    }

    /**
     * Sends a request to the language model while enforcing the use of a specific tool.
     *
     * @param tool The tool to force in the request.
     * @return The response from the language model.
     */
    public suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response {
        return requestLLMForceOneTool(tool.descriptor)
    }

    /**
     * Sends a request to the underlying LLM and returns the first non-reasoning response.
     *
     * @return The first response message from the LLM after executing the request.
     */
    public suspend fun requestLLM(): Message.Response {
        validateSession()
        return executeMultiple(prompt, tools).first { it !is Message.Reasoning }
    }

    /**
     * Sends a streaming request to the underlying LLM and returns the streamed response.
     *
     * @return A flow of streamed response frames.
     */
    public suspend fun requestLLMStreaming(): Flow<StreamFrame> {
        validateSession()
        return executeStreaming(prompt, tools)
    }

    /**
     * Sends a moderation request to the specified or default model.
     *
     * @param moderatingModel Optional model used for moderation.
     * @return Moderation result for the current prompt.
     */
    public suspend fun requestModeration(moderatingModel: LLModel? = null): ModerationResult {
        validateSession()
        val preparedPrompt = preparePrompt(prompt, emptyList())
        return executor.moderate(preparedPrompt, moderatingModel ?: model)
    }

    /**
     * Sends a request to the language model and returns all response messages.
     *
     * @return A list of responses from the language model.
     */
    public suspend fun requestLLMMultiple(): List<Message.Response> {
        validateSession()
        return executeMultiple(prompt, tools)
    }

    /**
     * Sends a request to LLM and gets a structured response.
     *
     * @param config A configuration defining structures and behavior.
     */
    public suspend fun <T> requestLLMStructured(
        config: StructuredRequestConfig<T>,
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> {
        validateSession()

        val preparedPrompt = preparePrompt(prompt, tools = emptyList())

        return executor.executeStructured(
            prompt = preparedPrompt,
            model = model,
            config = config,
        )
    }

    /**
     * Sends a request to LLM and gets a structured response.
     *
     * @param serializer Serializer for the requested structure type.
     * @param examples Optional examples that help the model follow the target format.
     * @param fixingParser Optional parser for malformed structured responses.
     */
    public suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> {
        validateSession()

        val preparedPrompt = preparePrompt(prompt, tools = emptyList())

        return executor.executeStructured(
            prompt = preparedPrompt,
            model = model,
            serializer = serializer,
            examples = examples,
            fixingParser = fixingParser,
        )
    }

    /**
     * Requests a structured response from the language model using a reified serializer.
     *
     * @param examples Optional examples that help the model follow the target format.
     * @param fixingParser Optional parser for malformed structured responses.
     */
    public suspend inline fun <reified T> requestLLMStructured(
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> = requestLLMStructured(
        serializer = serializer<T>(),
        examples = examples,
        fixingParser = fixingParser,
    )

    /**
     * Parses a structured response from a language model message using the specified configuration.
     *
     * @param response Assistant message to parse.
     * @param config Configuration describing the expected structure.
     * @param fixingParser Optional parser for malformed structured responses.
     */
    public suspend fun <T> parseResponseToStructuredResponse(
        response: Message.Assistant,
        config: StructuredRequestConfig<T>,
        fixingParser: StructureFixingParser? = null
    ): StructuredResponse<T> = executor.parseResponseToStructuredResponse(response, config, model, fixingParser)

    /**
     * Sends a request to the language model and returns all available response choices.
     */
    public suspend fun requestLLMMultipleChoices(): List<LLMChoice> {
        validateSession()
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeMultipleChoices(preparedPrompt, model, tools)
    }

    override fun close() {
        isActive = false
    }
}
