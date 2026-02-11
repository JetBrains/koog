@file:OptIn(InternalAgentsApi::class)
@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.utils.ActiveProperty
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.processor.executeProcessed
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.executeStructured
import ai.koog.prompt.structure.parseResponseToStructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

@OptIn(ExperimentalStdlibApi::class)
public open class AIAgentLLMSession(
    private val executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    prompt: Prompt,
    model: LLModel,
    responseProcessor: ResponseProcessor?,
    public open override val config: AIAgentConfig,
) : AIAgentLLMSessionAPI {
    public open override val prompt: Prompt by ActiveProperty(prompt) { isActive }

    public open override val tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }

    public open override val model: LLModel by ActiveProperty(model) { isActive }

    public open override val responseProcessor: ResponseProcessor? by ActiveProperty(responseProcessor) { isActive }

    public open override var isActive: Boolean = true

    @InternalAgentsApi
    public open override fun validateSession() {
        check(isActive) { "Cannot use session after it was closed" }
    }

    @InternalAgentsApi
    public open override fun preparePrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
        return config.missingToolsConversionStrategy.convertPrompt(prompt, tools)
    }

    @InternalAgentsApi
    public open override fun executeStreaming(prompt: Prompt, tools: List<ToolDescriptor>): Flow<StreamFrame> {
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeStreaming(preparedPrompt, model, tools)
    }

    @InternalAgentsApi
    public open override suspend fun executeMultiple(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeProcessed(preparedPrompt, model, tools, responseProcessor)
    }

    @InternalAgentsApi
    public open override suspend fun executeSingle(prompt: Prompt, tools: List<ToolDescriptor>): Message.Response =
        executeMultiple(prompt, tools).first()

    public open override suspend fun requestLLMMultipleWithoutTools(): List<Message.Response> {
        validateSession()

        val promptWithDisabledTools = prompt
            .withUpdatedParams { toolChoice = null }
            .let { preparePrompt(it, emptyList()) }

        return executeMultiple(promptWithDisabledTools, emptyList())
    }

    public open override suspend fun requestLLMWithoutTools(): Message.Response {
        validateSession()
        /*
            Not all LLM providers support a tool list when the tool choice is set to "none", so we are rewriting all tool messages to regular messages,
            for all requests without tools.
         */
        val promptWithDisabledTools = prompt
            .withUpdatedParams { toolChoice = null }
            .let { preparePrompt(it, emptyList()) }

        return executeMultiple(promptWithDisabledTools, emptyList()).first { it !is Message.Reasoning }
    }

    public open override suspend fun requestLLMOnlyCallingTools(): Message.Response {
        validateSession()
        val promptWithOnlyCallingTools = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Required
        }
        val responses = executeMultiple(promptWithOnlyCallingTools, tools)

        // some models might fail to produce a tool call
        // it's better to not fail here and allow the user to handle that
        return responses.firstOrNull { it is Message.Tool.Call }
            ?: responses.first { it is Message.Assistant }
    }

    public open override suspend fun requestLLMMultipleOnlyCallingTools(): List<Message.Response> {
        validateSession()
        val promptWithOnlyCallingTools = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Required
        }
        return executeMultiple(promptWithOnlyCallingTools, tools)
    }

    public open override suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response {
        validateSession()
        check(tools.contains(tool)) { "Unable to force call to tool `${tool.name}` because it is not defined" }
        val promptWithForcingOneTool = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Named(tool.name)
        }
        return executeSingle(promptWithForcingOneTool, tools)
    }

    public open override suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response {
        return requestLLMForceOneTool(tool.descriptor)
    }

    public open override suspend fun requestLLM(): Message.Response {
        validateSession()
        return executeMultiple(prompt, tools).first { it !is Message.Reasoning }
    }

    public open override suspend fun requestLLMStreaming(): Flow<StreamFrame> {
        validateSession()
        return executeStreaming(prompt, tools)
    }

    public open override suspend fun requestModeration(moderatingModel: LLModel?): ModerationResult {
        validateSession()
        val preparedPrompt = preparePrompt(prompt, emptyList())
        return executor.moderate(preparedPrompt, moderatingModel ?: model)
    }

    public open override suspend fun requestLLMMultiple(): List<Message.Response> {
        validateSession()
        return executeMultiple(prompt, tools)
    }

    public open override suspend fun <T> requestLLMStructured(
        config: StructuredRequestConfig<T>,
    ): Result<StructuredResponse<T>> {
        validateSession()

        val preparedPrompt = preparePrompt(prompt, tools = emptyList())

        return executor.executeStructured(
            prompt = preparedPrompt,
            model = model,
            config = config,
        )
    }

    public open override suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T>,
        fixingParser: StructureFixingParser?
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
     * Requests a structured response from the language model session, with optional examples for guidance
     * and a parser to fix structure-related issues in the response.
     *
     * @param T The type of the structured data expected in the response.
     * @param examples A list of examples to guide the language model in producing the desired structured response.
     *                 Defaults to an empty list if no examples are provided.
     * @param fixingParser An optional parser to validate and correct the structure of the language model's response.
     *                     Defaults to null if no fixing parser is provided.
     * @return A [Result] containing a [StructuredResponse] of type [T], which includes the structured data or error details.
     */
    public suspend inline fun <reified T> requestLLMStructured(
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> = requestLLMStructured(
        serializer = serializer<T>(),
        examples = examples,
        fixingParser = fixingParser,
    )

    public open override suspend fun <T> parseResponseToStructuredResponse(
        response: Message.Assistant,
        config: StructuredRequestConfig<T>
    ): StructuredResponse<T> = executor.parseResponseToStructuredResponse(response, config, model)

    public open override suspend fun requestLLMMultipleChoices(): List<LLMChoice> {
        validateSession()
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeMultipleChoices(preparedPrompt, model, tools)
    }

    final override fun close() {
        isActive = false
    }
}
