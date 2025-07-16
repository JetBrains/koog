@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.element.AgentRunInfoContextElement
import ai.koog.agents.core.agent.context.element.getAgentRunInfoElementOrThrow
import ai.koog.agents.core.agent.context.getAgentContextData
import ai.koog.agents.core.agent.context.removeAgentContextData
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.AIAgentEnvironmentUtils.mapToToolResult
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.exception.AgentEngineException
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.PromptExecutorProxy
import ai.koog.agents.core.model.AgentServiceError
import ai.koog.agents.core.model.AgentServiceErrorType
import ai.koog.agents.core.model.message.*
import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.utils.Closeable
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(InternalAgentToolsApi::class)
private class DirectToolCallsEnablerImpl : DirectToolCallsEnabler

@OptIn(InternalAgentToolsApi::class)
private class AllowDirectToolCallsContext(val toolEnabler: DirectToolCallsEnabler)

@OptIn(InternalAgentToolsApi::class)
private suspend inline fun <T> allowToolCalls(block: suspend AllowDirectToolCallsContext.() -> T) =
    AllowDirectToolCallsContext(DirectToolCallsEnablerImpl()).block()

/**
 * Represents an implementation of an AI agent that provides functionalities to execute prompts,
 * manage tools, handle agent pipelines, and interact with various configurable strategies and features.
 *
 * The agent operates within a coroutine scope and leverages a tool registry and feature context
 * to enable dynamic additions or configurations during its lifecycle. Its behavior is driven
 * by a local agent strategy and executed via a prompt executor.
 *
 * @param Input Type of agent input.
 * @param Output Type of agent output.
 *
 * @property inputType [KType] representing [Input] - agent input.
 * @property outputType [KType] representing [Output] - agent output.
 * @property promptExecutor Executor used to manage and execute prompt strings.
 * @property strategy Strategy defining the local behavior of the agent.
 * @property agentConfig Configuration details for the local agent that define its operational parameters.
 * @property toolRegistry Registry of tools the agent can interact with, defaulting to an empty registry.
 * @property installFeatures Lambda for installing additional features within the agent environment.
 * @property clock The clock used to calculate message timestamps
 * @constructor Initializes the AI agent instance and prepares the feature context and pipeline for use.
 */
@OptIn(ExperimentalUuidApi::class)
public open class AIAgent<Input, Output>(
    public val inputType: KType,
    public val outputType: KType,
    public val promptExecutor: PromptExecutor,
    private val strategy: AIAgentStrategy<Input, Output>,
    public val agentConfig: AIAgentConfigBase,
    override val id: String = Uuid.random().toString(),
    public val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    public val clock: Clock = Clock.System,
    private val installFeatures: FeatureContext.() -> Unit = {},
) : AIAgentBase<Input, Output>, AIAgentEnvironment, Closeable {

    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * The context for adding and configuring features in a Kotlin AI Agent instance.
     *
     * Note: The method is used to hide internal install() method from a public API to prevent
     *       calls in an [AIAgent] instance, like `agent.install(MyFeature) { ... }`.
     *       This makes the API a bit stricter and clear.
     */
    public class FeatureContext internal constructor(private val agent: AIAgent<*, *>) {
        /**
         * Installs and configures a feature into the current AI agent context.
         *
         * @param feature the feature to be added, defined by an implementation of [AIAgentFeature], which provides specific functionality
         * @param configure an optional lambda to customize the configuration of the feature, where the provided [Config] can be modified
         */
        public fun <Config : FeatureConfig, Feature : Any> install(
            feature: AIAgentFeature<Config, Feature>,
            configure: Config.() -> Unit = {}
        ) {
            agent.install(feature, configure)
        }
    }

    private var isRunning = false

    private val runningMutex = Mutex()

    private val pipeline = AIAgentPipeline()

    init {
        FeatureContext(this).installFeatures()
    }

    override suspend fun run(agentInput: Input): Output {
        runningMutex.withLock {
            if (isRunning) {
                throw IllegalStateException("Agent is already running")
            }

            isRunning = true
        }

        pipeline.prepareFeatures()

        val sessionUuid = Uuid.random()
        val runId = sessionUuid.toString()

        return withContext(
            AgentRunInfoContextElement(
                agentId = id,
                runId = runId,
                agentConfig = agentConfig,
                strategyName = strategy.name
            )
        ) {
            val stateManager = AIAgentStateManager()
            val storage = AIAgentStorage()

            // Environment (initially equal to the current agent), transformed by some features
            //   (ex: testing feature transforms it into a MockEnvironment with mocked tools)
            val preparedEnvironment =
                pipeline.transformEnvironment(strategy = strategy, agent = this@AIAgent, baseEnvironment = this@AIAgent)

            val agentContext = AIAgentContext(
                environment = preparedEnvironment,
                agentInput = agentInput,
                agentInputType = inputType,
                config = agentConfig,
                llm = AIAgentLLMContext(
                    tools = toolRegistry.tools.map { it.descriptor },
                    toolRegistry = toolRegistry,
                    prompt = agentConfig.prompt,
                    model = agentConfig.model,
                    promptExecutor = PromptExecutorProxy(
                        executor = promptExecutor,
                        pipeline = pipeline,
                        runId = runId
                    ),
                    environment = preparedEnvironment,
                    config = agentConfig,
                    clock = clock
                ),
                stateManager = stateManager,
                storage = storage,
                runId = runId,
                strategyName = strategy.name,
                pipeline = pipeline,
                id = id,
            )

            logger.debug { formatLog(agentId = id, runId = runId, message = "Starting agent execution") }
            pipeline.onBeforeAgentStarted(
                runId = runId,
                agent = this@AIAgent,
                strategy = strategy,
                context = agentContext
            )

            setExecutionPointIfNeeded(agentContext)

            var result = strategy.execute(context = agentContext, input = agentInput)
            while (result == null && agentContext.getAgentContextData() != null) {
                setExecutionPointIfNeeded(agentContext)
                result = strategy.execute(context = agentContext, input = agentInput)
            }

            logger.debug { formatLog(agentId = id, runId = runId, message = "Finished agent execution") }
            pipeline.onAgentFinished(agentId = id, runId = runId, result = result, resultType = outputType)

            runningMutex.withLock {
                isRunning = false
            }

            return@withContext result ?: error("result is null")
        }
    }

    private suspend fun setExecutionPointIfNeeded(
        agentContext: AIAgentContext
    ) {
        val additionalContextData = agentContext.getAgentContextData()
        if (additionalContextData == null) {
            return
        }

        additionalContextData.let { contextData ->
            val nodeId = contextData.nodeId
            strategy.setExecutionPoint(nodeId, contextData.lastInput ?: error("lastInput is null"))
            val messages = contextData.messageHistory
            agentContext.llm.withPrompt {
                this.withMessages { (messages).sortedBy { m -> m.metaInfo.timestamp } }
            }
        }

        agentContext.removeAgentContextData()
    }

    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElementOrThrow()

        logger.info {
            formatLog(
                agentRunInfo.agentId,
                agentRunInfo.runId,
                "Executing tools: [${toolCalls.joinToString(", ") { it.tool }}]"
            )
        }

        val message = AgentToolCallsToEnvironmentMessage(
            runId = agentRunInfo.runId,
            content = toolCalls.map { call ->
                AgentToolCallToEnvironmentContent(
                    agentId = id,
                    runId = agentRunInfo.runId,
                    toolCallId = call.id,
                    toolName = call.tool,
                    toolArgs = call.contentJson
                )
            }
        )

        val results = processToolCallMultiple(message).mapToToolResult()
        logger.debug {
            "Received results from tools call (" +
                    "tools: [${toolCalls.joinToString(", ") { it.tool }}], " +
                    "results: [${results.joinToString(", ") { it.result?.toStringDefault() ?: "null" }}])"
        }

        return results
    }

    override suspend fun reportProblem(exception: Throwable) {
        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElementOrThrow()

        logger.error(exception) {
            formatLog(agentRunInfo.agentId, agentRunInfo.runId, "Reporting problem: ${exception.message}")
        }

        processError(
            agentId = agentRunInfo.agentId,
            runId = agentRunInfo.runId,
            error = AgentServiceError(
                type = AgentServiceErrorType.UNEXPECTED_ERROR,
                message = exception.message ?: "unknown error"
            )
        )
    }

    override suspend fun close() {
        pipeline.onAgentBeforeClosed(agentId = id)
        pipeline.closeFeaturesStreamProviders()
    }

    //region Private Methods

    private fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        pipeline.install(feature, configure)
    }

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(content: AgentToolCallToEnvironmentContent): EnvironmentToolResultToAgentContent =
        allowToolCalls {
            logger.debug { "Handling tool call sent by server..." }
            val tool = toolRegistry.getTool(content.toolName)
            // Tool Args
            val toolArgs = try {
                tool.decodeArgs(content.toolArgs)
            } catch (e: Exception) {
                logger.error(e) { "Tool \"${tool.name}\" failed to parse arguments: ${content.toolArgs}" }
                return toolResult(
                    message = "Tool \"${tool.name}\" failed to parse arguments because of ${e.message}!",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            }

            pipeline.onToolCall(
                runId = content.runId,
                toolCallId = content.toolCallId,
                tool = tool,
                toolArgs = toolArgs
            )

            // Tool Execution
            val toolResult = try {
                @Suppress("UNCHECKED_CAST")
                (tool as Tool<ToolArgs, ToolResult>).execute(toolArgs, toolEnabler)
            } catch (e: ToolException) {

                pipeline.onToolValidationError(
                    runId = content.runId,
                    toolCallId = content.toolCallId,
                    tool = tool,
                    toolArgs = toolArgs,
                    error = e.message
                )

                return toolResult(
                    message = e.message,
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            } catch (e: Exception) {

                logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${content.toolArgs}" }

                pipeline.onToolCallFailure(
                    runId = content.runId,
                    toolCallId = content.toolCallId,
                    tool = tool,
                    toolArgs = toolArgs,
                    throwable = e
                )

                return toolResult(
                    message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            }

            // Tool Finished with Result
            pipeline.onToolCallResult(
                runId = content.runId,
                toolCallId = content.toolCallId,
                tool = tool,
                toolArgs = toolArgs,
                result = toolResult
            )

            logger.debug { "Completed execution of ${content.toolName} with result: $toolResult" }

            return toolResult(
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = strategy.name,
                message = toolResult.toStringDefault(),
                result = toolResult
            )
        }

    private suspend fun processToolCallMultiple(message: AgentToolCallsToEnvironmentMessage): EnvironmentToolResultMultipleToAgentMessage {
        // call tools in parallel and return results
        val results = supervisorScope {
            message.content
                .map { call -> async { processToolCall(call) } }
                .awaitAll()
        }

        return EnvironmentToolResultMultipleToAgentMessage(
            runId = message.runId,
            content = results
        )
    }

    private fun toolResult(
        toolCallId: String?,
        toolName: String,
        agentId: String,
        message: String,
        result: ToolResult?
    ): EnvironmentToolResultToAgentContent = AIAgentEnvironmentToolResultToAgentContent(
        toolCallId = toolCallId,
        toolName = toolName,
        agentId = agentId,
        message = message,
        toolResult = result
    )

    private suspend fun processError(agentId: String, runId: String, error: AgentServiceError) {
        try {
            throw error.asException()
        } catch (e: AgentEngineException) {
            logger.error(e) { "Execution exception reported by server!" }
            pipeline.onAgentRunError(agentId = agentId, runId = runId, throwable = e)
        }
    }

    private fun formatLog(agentId: String, runId: String, message: String): String =
        "[agent id: $agentId, run id: $runId] $message"

    //endregion Private Methods
}

/**
 * Convenience builder that creates an instance of [AIAgent], automatically deducing [AIAgent.inputType] and [AIAgent.outputType]
 * from [Input] and [Output]
 *
 * @property promptExecutor Executor used to manage and execute prompt strings.
 * @property strategy Strategy defining the local behavior of the agent.
 * @property agentConfig Configuration details for the local agent that define its operational parameters.
 * @property toolRegistry Registry of tools the agent can interact with, defaulting to an empty registry.
 * @property installFeatures Lambda for installing additional features within the agent environment.
 * @property clock The clock used to calculate message timestamps
 *
 * @see [AIAgent] class
 */
@OptIn(ExperimentalUuidApi::class)
public inline fun <reified Input, reified Output> AIAgent(
    promptExecutor: PromptExecutor,
    strategy: AIAgentStrategy<Input, Output>,
    agentConfig: AIAgentConfigBase,
    id: String = Uuid.random().toString(),
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    clock: Clock = Clock.System,
    noinline installFeatures: FeatureContext.() -> Unit = {},
): AIAgent<Input, Output> = AIAgent(
    inputType = typeOf<Input>(),
    outputType = typeOf<Output>(),
    promptExecutor = promptExecutor,
    strategy = strategy,
    agentConfig = agentConfig,
    id = id,
    toolRegistry = toolRegistry,
    clock = clock,
    installFeatures = installFeatures,
)

/**
 * Convenience builder that creates an instance of an [AIAgent] with string input and output and the specified parameters.
 *
 * @param executor The [PromptExecutor] responsible for executing prompts.
 * @param strategy The [AIAgentStrategy] defining the agent's behavior. Default is a single-run strategy.
 * @param systemPrompt The system-level prompt context for the agent. Default is an empty string.
 * @param llmModel The language model to be used by the agent.
 * @param temperature The sampling temperature for the language model, controlling randomness. Default is 1.0.
 * @param toolRegistry The [ToolRegistry] containing tools available to the agent. Default is an empty registry.
 * @param maxIterations Maximum number of iterations for the agent's execution. Default is 50.
 * @param installFeatures A suspending lambda to install additional features for the agent's functionality. Default is an empty lambda.
 *
 * @see [AIAgent] class
 */
@OptIn(ExperimentalUuidApi::class)
public fun AIAgent(
    executor: PromptExecutor,
    llmModel: LLModel,
    id: String = Uuid.random().toString(),
    strategy: AIAgentStrategy<String, String> = singleRunStrategy(),
    systemPrompt: String = "",
    temperature: Double = 1.0,
    numberOfChoices: Int = 1,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    maxIterations: Int = 50,
    installFeatures: FeatureContext.() -> Unit = {}
): AIAgent<String, String> = AIAgent(
    id = id,
    promptExecutor = executor,
    strategy = strategy,
    agentConfig = AIAgentConfig(
        prompt = prompt(
            id = "chat",
            params = LLMParams(
                temperature = temperature,
                numberOfChoices = numberOfChoices
            )
        ) {
            system(systemPrompt)
        },
        model = llmModel,
        maxAgentIterations = maxIterations,
    ),
    toolRegistry = toolRegistry,
    installFeatures = installFeatures
)