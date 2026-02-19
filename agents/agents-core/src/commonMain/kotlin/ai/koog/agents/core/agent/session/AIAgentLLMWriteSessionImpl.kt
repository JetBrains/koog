@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ActiveProperty
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

@PublishedApi
internal class AIAgentLLMWriteSessionImpl internal constructor(
    @get:JvmName("environment")
    override val environment: AIAgentEnvironment,
    private val executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    @get:JvmName("toolRegistry")
    override val toolRegistry: ToolRegistry,
    prompt: Prompt,
    model: LLModel,
    responseProcessor: ResponseProcessor?,
    internal val config: AIAgentConfig,
    @get:JvmName("clock")
    override val clock: Clock,
) : AIAgentLLMWriteSessionAPI {

    private val delegate
        get() = AIAgentLLMReadSessionImpl(executor, tools, prompt, model, responseProcessor, config, isActive)

    override var prompt: Prompt by ActiveProperty(prompt) { isActive }

    override var tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }

    override var model: LLModel by ActiveProperty(model) { isActive }

    override var responseProcessor: ResponseProcessor? by ActiveProperty(responseProcessor) { isActive }

    private var isActive: Boolean = true

    override fun <TArgs, TResult> findTool(tool: Tool<TArgs, TResult>): SafeTool<TArgs, TResult> {
        return findTool(tool::class)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <TArgs, TResult> findTool(toolClass: KClass<out Tool<TArgs, TResult>>): SafeTool<TArgs, TResult> {
        val tool = toolRegistry.tools.find(toolClass::isInstance) as? Tool<TArgs, TResult>
            ?: throw IllegalArgumentException("Tool with type ${toolClass.simpleName} is not defined")

        return SafeTool(tool, environment, clock)
    }

    override fun appendPrompt(body: PromptBuilder.() -> Unit) {
        prompt = prompt(prompt, clock, body)
    }

    @Deprecated("Use `appendPrompt` instead", ReplaceWith("appendPrompt(body)"))
    override fun updatePrompt(body: PromptBuilder.() -> Unit) {
        appendPrompt(body)
    }

    override fun rewritePrompt(body: (prompt: Prompt) -> Prompt) {
        prompt = body(prompt)
    }

    override fun changeModel(newModel: LLModel) {
        model = newModel
    }

    override fun changeLLMParams(newParams: LLMParams) = rewritePrompt {
        prompt.withParams(newParams)
    }

    override suspend fun requestLLMMultipleWithoutTools(): List<Message.Response> {
        return delegate.requestLLMMultipleWithoutTools().also { responses ->
            appendPrompt { messages(responses) }
        }
    }

    override suspend fun requestLLMWithoutTools(): Message.Response {
        config
        return delegate.requestLLMWithoutTools().also { response -> appendPrompt { message(response) } }
    }

    override suspend fun requestLLMOnlyCallingTools(): Message.Response {
        return delegate.requestLLMOnlyCallingTools()
            .also { response -> appendPrompt { message(response) } }
    }

    override suspend fun requestLLMMultipleOnlyCallingTools(): List<Message.Response> {
        return delegate.requestLLMMultipleOnlyCallingTools()
            .also { responses ->
                appendPrompt { messages(responses) }
            }
    }

    override suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response {
        return delegate.requestLLMForceOneTool(tool)
            .also { response -> appendPrompt { message(response) } }
    }

    override suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response {
        return delegate.requestLLMForceOneTool(tool)
            .also { response -> appendPrompt { message(response) } }
    }

    override suspend fun requestLLM(): Message.Response {
        return delegate.requestLLM().also { response ->
            appendPrompt { message(response) }
        }
    }

    override suspend fun requestLLMStreaming(): Flow<StreamFrame> {
        return delegate.requestLLMStreaming()
    }

    override suspend fun requestModeration(moderatingModel: LLModel?): ModerationResult {
        return delegate.requestModeration(moderatingModel)
    }

    override suspend fun requestLLMMultiple(): List<Message.Response> {
        return delegate.requestLLMMultiple().also { responses ->
            appendPrompt {
                responses.forEach { message(it) }
            }
        }
    }

    override suspend fun <T> requestLLMStructured(
        config: StructuredRequestConfig<T>,
    ): Result<StructuredResponse<T>> {
        return delegate.requestLLMStructured(config).also {
            it.onSuccess { response ->
                appendPrompt {
                    message(response.message)
                }
            }
        }
    }

    @PublishedApi
    internal suspend inline fun <reified T> requestLLMStructuredImpl(
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> = requestLLMStructured(
        serializer = serializer<T>(),
        examples = examples,
        fixingParser = fixingParser,
    )

    override suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T>,
        fixingParser: StructureFixingParser?
    ): Result<StructuredResponse<T>> {
        return delegate.requestLLMStructured(serializer, examples, fixingParser).also {
            it.onSuccess { response ->
                appendPrompt {
                    message(response.message)
                }
            }
        }
    }

    override suspend fun <T> parseResponseToStructuredResponse(
        response: Message.Assistant,
        config: StructuredRequestConfig<T>
    ): StructuredResponse<T> {
        return delegate.parseResponseToStructuredResponse(response, config)
    }

    override suspend fun requestLLMMultipleChoices(): List<LLMChoice> {
        return delegate.requestLLMMultipleChoices()
    }

    override suspend fun requestLLMStreaming(definition: StructureDefinition?): Flow<StreamFrame> {
        if (definition != null) {
            val prompt = prompt(prompt, clock) {
                user {
                    definition.definition(this)
                }
            }
            this.prompt = prompt
        }

        return delegate.requestLLMStreaming()
    }

    @PublishedApi
    internal inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsImpl(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> = flatMapMerge(concurrency) { args ->
        flow {
            emit(safeTool.execute(args))
        }
    }

    @PublishedApi
    internal inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRawImpl(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<String> = flatMapMerge(concurrency) { args ->
        flow {
            emit(safeTool.executeRaw(args))
        }
    }

    @PublishedApi
    internal inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsImpl(
        tool: Tool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> = flatMapMerge(concurrency) { args ->
        val safeTool = findTool(tool::class)
        flow {
            emit(safeTool.execute(args))
        }
    }

    @PublishedApi
    internal inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsImpl(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> {
        val tool = findTool(toolClass)
        return toParallelToolCallsImpl(tool, concurrency)
    }

    @PublishedApi
    internal inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRawImpl(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<String> {
        val tool = findTool(toolClass)
        return toParallelToolCallsRawImpl(tool, concurrency)
    }

    override fun close() {
        isActive = false
    }
}
