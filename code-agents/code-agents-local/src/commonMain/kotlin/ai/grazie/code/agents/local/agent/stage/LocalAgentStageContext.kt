@file:OptIn(InternalAgentsApi::class)

package ai.grazie.code.agents.local.agent.stage

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.utils.ActiveProperty
import ai.grazie.code.agents.core.utils.RWLock
import ai.grazie.code.agents.local.InternalAgentsApi
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.agent.LocalAgentStateManager
import ai.grazie.code.agents.local.agent.LocalAgentStorage
import ai.grazie.code.agents.local.agent.LocalAgentStorageKey
import ai.grazie.code.agents.local.environment.AgentEnvironment
import ai.grazie.code.agents.local.environment.SafeTool
import ai.grazie.code.agents.local.environment.executeTool
import ai.grazie.code.agents.local.features.AIAgentPipeline
import ai.grazie.code.agents.local.features.KotlinAIAgentFeature
import ai.grazie.code.prompt.structure.*
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.dsl.PromptBuilder
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.llm.JetBrainsAIModels
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KClass

interface LocalAgentStageContext {
    val environment: AgentEnvironment
    val stageInput: String
    val config: LocalAgentConfig
    val llm: LocalAgentLLMContext
    val stateManager: LocalAgentStateManager
    val storage: LocalAgentStorage
    val sessionUuid: UUID
    val strategyId: String
    val stageName: String

    @InternalAgentsApi
    val pipeline: AIAgentPipeline

    @Suppress("UNCHECKED_CAST")
    fun <Feature : Any> feature(key: LocalAgentStorageKey<Feature>): Feature?

    fun <Feature : Any> feature(feature: KotlinAIAgentFeature<*, Feature>): Feature?

    @InternalAgentsApi
    fun copyWithTools(tools: List<ToolDescriptor>): LocalAgentStageContext {
        return this.copy(llm = llm.copy(tools = tools))
    }

    fun copy(
        environment: AgentEnvironment? = null,
        stageInput: String? = null,
        config: LocalAgentConfig? = null,
        llm: LocalAgentLLMContext? = null,
        stateManager: LocalAgentStateManager? = null,
        storage: LocalAgentStorage? = null,
        sessionUuid: UUID? = null,
        strategyId: String? = null,
        stageName: String? = null,
        pipeline: AIAgentPipeline? = null,
    ): LocalAgentStageContext
}

class LocalAgentStageContextImpl constructor(
    override val environment: AgentEnvironment,
    override val stageInput: String,
    override val config: LocalAgentConfig,
    override val llm: LocalAgentLLMContext,
    override val stateManager: LocalAgentStateManager,
    override val storage: LocalAgentStorage,
    override val sessionUuid: UUID,
    override val strategyId: String,
    override val stageName: String,
    override val pipeline: AIAgentPipeline,
) : LocalAgentStageContext {
    private val features: Map<LocalAgentStorageKey<*>, Any> =
        pipeline.getStageFeatures(this)

    @Suppress("UNCHECKED_CAST")
    override fun <Feature : Any> feature(key: LocalAgentStorageKey<Feature>): Feature? = features[key] as Feature?

    override fun <Feature : Any> feature(feature: KotlinAIAgentFeature<*, Feature>): Feature? = feature(feature.key)

    override fun copyWithTools(tools: List<ToolDescriptor>): LocalAgentStageContext {
        return this.copy(llm = llm.copy(tools = tools))
    }

    override fun copy(
        environment: AgentEnvironment?,
        stageInput: String?,
        config: LocalAgentConfig?,
        llm: LocalAgentLLMContext?,
        stateManager: LocalAgentStateManager?,
        storage: LocalAgentStorage?,
        sessionUuid: UUID?,
        strategyId: String?,
        stageName: String?,
        pipeline: AIAgentPipeline?,
    ) = LocalAgentStageContextImpl(
        environment = environment ?: this.environment,
        stageInput = stageInput ?: this.stageInput,
        config = config ?: this.config,
        llm = llm ?: this.llm,
        stateManager = stateManager ?: this.stateManager,
        storage = storage ?: this.storage,
        sessionUuid = sessionUuid ?: this.sessionUuid,
        strategyId = strategyId ?: this.strategyId,
        stageName = stageName ?: this.stageName,
        pipeline = pipeline ?: this.pipeline,
    )
}

data class LocalAgentLLMContext(
    internal var tools: List<ToolDescriptor>,
    val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    private var prompt: Prompt,
    internal val promptExecutor: CodePromptExecutor,
    private val environment: AgentEnvironment
) {

    private val rwLock = RWLock()

    /**
     * Executes a write session on the LocalAgentLLMContext, ensuring that all active write and read sessions are completed
     * before initiating the write session.
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun <T> writeSession(block: suspend LocalAgentLLMWriteSession.() -> T): T = rwLock.withWriteLock {
        val session = LocalAgentLLMWriteSession(environment, promptExecutor, tools, toolRegistry, prompt)

        session.use {
            val result = it.block()

            // update tools and prompt after session execution
            this.prompt = it.prompt
            this.tools = it.tools

            result
        }
    }

    /**
     * Executes a read session within the LocalAgentLLMContext, ensuring concurrent safety
     * with active write session and other read sessions.
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun <T> readSession(block: suspend LocalAgentLLMReadSession.() -> T): T = rwLock.withReadLock {
        val session = LocalAgentLLMReadSession(tools, promptExecutor, prompt)

        session.use { block(it) }
    }

}

@OptIn(ExperimentalStdlibApi::class)
sealed class LocalAgentLLMSession(
    protected val executor: CodePromptExecutor,
    tools: List<ToolDescriptor>,
    prompt: Prompt
) : AutoCloseable {
    open val prompt: Prompt by ActiveProperty(prompt) { isActive }
    open val tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }


    protected var isActive = true

    protected fun validateSession() {
        check(isActive) { "Cannot use session after it was closed" }
    }

    open suspend fun requestLLMWithoutTools(): Message.Response {
        validateSession()
        return executor.execute(prompt, emptyList()).first()
    }

    open suspend fun requestLLM(): Message.Response {
        validateSession()
        return executor.execute(prompt, tools).first()
    }

    open suspend fun requestLLMMultiple(): List<Message.Response> {
        validateSession()
        return executor.execute(prompt, tools)
    }

    /**
     * Coerce LLM to provide a structured output.
     *
     * @see [executeStructured]
     */
    open suspend fun <T> requestLLMStructured(
        structure: StructuredData<T>,
        retries: Int = 1,
        fixingModel: LLModel = JetBrainsAIModels.OpenAI.GPT4_1_Mini
    ): StructuredResponse<T> {
        validateSession()
        return executor.executeStructured(prompt, structure, retries, fixingModel)
    }

    /**
     * Expect LLM to reply in a structured format and try to parse it.
     * For more robust version with model coercion and correction see [requestLLMStructured]
     *
     * @see [executeStructuredOneShot]
     */
    open suspend fun <T> requestLLMStructuredOneShot(structure: StructuredData<T>): StructuredResponse<T> {
        validateSession()
        return executor.executeStructuredOneShot(prompt, structure)
    }

    final override fun close() {
        isActive = false
    }
}

@Suppress("unused")
class LocalAgentLLMWriteSession internal constructor(
    val environment: AgentEnvironment,
    executor: CodePromptExecutor,
    tools: List<ToolDescriptor>,
    val toolRegistry: ToolRegistry,
    prompt: Prompt,
) : LocalAgentLLMSession(executor, tools, prompt) {
    override var prompt: Prompt by ActiveProperty(prompt) { isActive }
    override var tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }


    @Suppress("UNCHECKED_CAST")
    suspend inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> callTool(
        tool: Tool<TArgs, TResult>,
        args: TArgs
    ): SafeTool.Result<TResult> {
        return findTool(tool::class).execute(args)
    }

    @Suppress("UNCHECKED_CAST")
    suspend inline fun <reified TArgs : Tool.Args> callTool(
        toolName: String,
        args: TArgs
    ): SafeTool.Result<out ToolResult> {
        return findToolByName<TArgs>(toolName).execute(args)
    }

    @Suppress("UNCHECKED_CAST")
    suspend inline fun <reified TArgs : Tool.Args> callToolRaw(
        toolName: String,
        args: TArgs
    ): String {
        return findToolByName<TArgs>(toolName).executeRaw(args)
    }

    @Suppress("UNCHECKED_CAST")
    suspend inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> callTool(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        args: TArgs
    ): SafeTool.Result<TResult> {
        val tool = findTool(toolClass)
        return tool.execute(args)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> findTool(toolClass: KClass<out Tool<TArgs, TResult>>): SafeTool<TArgs, TResult> {
        val tool = (toolRegistry.stages.first().tools.find(toolClass::isInstance) as? Tool<TArgs, TResult>
            ?: throw IllegalArgumentException("Tool with type ${toolClass.simpleName} is not defined"))

        return SafeTool(tool, environment)
    }

    @Suppress("UNCHECKED_CAST")
    suspend inline fun <reified ToolT : Tool<*, *>> callTool(
        args: Tool.Args
    ): SafeTool.Result<out ToolResult> {
        val tool = findTool<ToolT>()
        return tool.executeUnsafe(args)
    }

    inline fun <reified ToolT : Tool<*, *>> findTool(): SafeTool<*, *> {
        val tool = toolRegistry.stages.first().tools.find(ToolT::class::isInstance) as? ToolT
            ?: throw IllegalArgumentException("Tool with type ${ToolT::class.simpleName} is not defined")

        return SafeTool(tool, environment)
    }

    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCalls(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> = flatMapMerge(concurrency) { args ->
        flow {
            emit(safeTool.execute(args))
        }
    }

    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCallsRaw(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<String> = flatMapMerge(concurrency) { args ->
        flow {
            emit(safeTool.executeRaw(args))
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCalls(
        tool: Tool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> = flatMapMerge(concurrency) { args ->
        val safeTool = findTool(tool::class)
        flow {
            emit(safeTool.execute(args))
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCalls(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> {
        val tool = findTool(toolClass)
        return toParallelToolCalls(tool, concurrency)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCallsRaw(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<String> {
        val tool = findTool(toolClass)
        return toParallelToolCallsRaw(tool, concurrency)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> findToolByNameAndArgs(toolName: String): Tool<TArgs, TResult> =
        (toolRegistry.getTool(toolName) as? Tool<TArgs, TResult>
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined or has incompatible arguments"))

    @Suppress("UNCHECKED_CAST")
    inline fun <reified TArgs : Tool.Args> findToolByName(toolName: String): SafeTool<TArgs, *> {
        val tool = (toolRegistry.getTool(toolName) as? Tool<TArgs, *>
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined or has incompatible arguments"))

        return SafeTool(tool, environment)
    }

    fun updatePrompt(body: PromptBuilder.() -> Unit) {
        prompt = prompt(prompt, body)
    }

    fun rewritePrompt(body: (prompt: Prompt) -> Prompt) {
        prompt = body(prompt)
    }

    override suspend fun requestLLMWithoutTools(): Message.Response {
        return super.requestLLMWithoutTools().also { response -> updatePrompt { message(response) } }
    }

    override suspend fun requestLLM(): Message.Response {
        return super.requestLLM().also { response -> updatePrompt { message(response) } }
    }

    override suspend fun requestLLMMultiple(): List<Message.Response> {
        return super.requestLLMMultiple().also { responses ->
            updatePrompt {
                responses.forEach { message(it) }
            }
        }
    }

    /**
     * @see [requestLLMStructured]
     */
    override suspend fun <T> requestLLMStructured(
        structure: StructuredData<T>,
        retries: Int,
        fixingModel: LLModel
    ): StructuredResponse<T> {
        return super.requestLLMStructured(structure, retries, fixingModel).also { response ->
            updatePrompt {
                assistant(response.raw)
            }
        }
    }

    suspend fun requestLLMStreaming(definition: StructuredDataDefinition? = null): Flow<String> {
        if (definition != null) {
            val prompt = prompt(prompt) {
                user {
                    definition.definition(this)
                }
            }
            this.prompt = prompt
        }

        return executor.executeStreaming(prompt)
    }

    /**
     * @see [requestLLMStructuredOneShot]
     */
    override suspend fun <T> requestLLMStructuredOneShot(structure: StructuredData<T>): StructuredResponse<T> {
        return super.requestLLMStructuredOneShot(structure).also { response ->
            updatePrompt {
                assistant(response.raw)
            }
        }
    }
}

class LocalAgentLLMReadSession internal constructor(
    tools: List<ToolDescriptor>,
    executor: CodePromptExecutor,
    prompt: Prompt,
) : LocalAgentLLMSession(executor, tools, prompt)
