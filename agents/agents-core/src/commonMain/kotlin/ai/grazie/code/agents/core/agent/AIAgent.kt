package ai.grazie.code.agents.core.agent

import ai.grazie.code.agents.core.agent.config.AIAgentConfig
import ai.grazie.code.agents.core.agent.entity.AIAgentStrategy
import ai.grazie.code.agents.core.api.AIAgentBase
import ai.grazie.code.agents.core.environment.AIAgentEnvironment
import ai.grazie.code.agents.core.environment.AIAgentEnvironmentUtils.mapToToolResult
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.environment.TerminationTool
import ai.grazie.code.agents.core.exception.AgentEngineException
import ai.grazie.code.agents.core.feature.AIAgentPipeline
import ai.grazie.code.agents.core.feature.AIAgentFeature
import ai.grazie.code.agents.core.model.AgentServiceError
import ai.grazie.code.agents.core.model.AgentServiceErrorType
import ai.grazie.code.agents.core.model.message.*
import ai.grazie.code.agents.core.tools.*
import ai.grazie.code.agents.core.tools.annotations.InternalAgentToolsApi
import ai.grazie.code.agents.core.tools.tools.ToolStage
import ai.grazie.code.agents.local.features.common.config.FeatureConfig
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.SuitableForIO
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.text.TextContentBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
 * @property promptExecutor Executor used to manage and execute prompt strings.
 * @property strategy Strategy defining the local behavior of the agent.
 * @property agentConfig Configuration details for the local agent that define its operational parameters.
 * @property toolRegistry Registry of tools the agent can interact with, defaulting to an empty registry.
 * @property installFeatures Lambda for installing additional features within the agent environment.
 * @constructor Initializes the AI agent instance and prepares the feature context and pipeline for use.
 */
public open class AIAgent(
    public val promptExecutor: PromptExecutor,
    private val strategy: AIAgentStrategy,
    cs: CoroutineScope,
    public val agentConfig: AIAgentConfig,
    public val toolRegistry: ToolRegistry = ToolRegistry.Companion.EMPTY,
    private val installFeatures: suspend FeatureContext.() -> Unit = {}
) : AIAgentBase, AIAgentEnvironment {

    private companion object {
        private val logger = LoggerFactory.create("ai.grazie.code.agents.core.agent.${AIAgent::class.simpleName}")
        private const val INVALID_TOOL = "Can not call tools beside \"${TerminationTool.NAME}\"!"
        private const val NO_CONTENT = "Could not find \"content\", but \"error\" is also absent!"
        private const val NO_RESULT = "Required tool argument value not found: \"${TerminationTool.ARG}\"!"
    }

    /**
     * The context for adding and configuring features in a Kotlin AI Agent instance.
     *
     * Note: The method is used to hide internal install() method from a public API to prevent
     *       calls in an [KotlinAIAgent] instance, like `agent.install(MyFeature) { ... }`.
     *       This makes the API a bit stricter and clear.
     */
    public class FeatureContext internal constructor(private val agent: AIAgent) {
        public suspend fun <Config : FeatureConfig, Feature : Any> install(
            feature: AIAgentFeature<Config, Feature>,
            configure: Config.() -> Unit = {}
        ) {
            agent.install(feature, configure)
        }
    }

    private var isRunning = false

    private var sessionUuid: UUID? = null

    private val runningMutex = Mutex()

    private val agentResultDeferred: CompletableDeferred<String?> = CompletableDeferred()

    private val pipeline = AIAgentPipeline()

    init {
        cs.launch(context = Dispatchers.SuitableForIO, start = CoroutineStart.UNDISPATCHED) {
            FeatureContext(this@AIAgent).installFeatures()
            pipeline.onAgentCreated(strategy, this@AIAgent)
        }
    }

    override suspend fun run(prompt: String) {
        pipeline.awaitFeaturesStreamProvidersReady()

        runningMutex.withLock {
            if (isRunning) {
                throw IllegalStateException("Agent is already running")
            }

            isRunning = true
            sessionUuid = UUID.random()
        }

        pipeline.onAgentStarted(strategyName = strategy.name)

        strategy.run(
            sessionUuid = sessionUuid ?: throw IllegalStateException("Session UUID is null"),
            userInput = prompt,
            toolRegistry = toolRegistry,
            promptExecutor = promptExecutor,
            environment = this,
            config = agentConfig,
            pipeline = pipeline
        )

        pipeline.closeFeaturesStreamProviders()

        runningMutex.withLock {
            isRunning = false
            sessionUuid = null
            if (!agentResultDeferred.isCompleted) {
                agentResultDeferred.complete(null)
            }
        }
    }

    public suspend fun run(builder: suspend TextContentBuilder.() -> Unit) {
        val prompt = TextContentBuilder().apply { this.builder() }.build()
        run(prompt = prompt)
    }

    override suspend fun runAndGetResult(userPrompt: String): String? {
        run(userPrompt)
        agentResultDeferred.await()
        return agentResultDeferred.getCompleted()
    }

    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        logger.info { formatLog("Executing tools: [${toolCalls.joinToString(", ") { it.tool }}]") }

        val message = AgentToolCallsToEnvironmentMessage(
            sessionUuid = sessionUuid ?: throw IllegalStateException("Session UUID is null"),
            content = toolCalls.map { call ->
                AgentToolCallToEnvironmentContent(
                    agentId = strategy.name,
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
        logger.error(exception) { formatLog("Reporting problem: ${exception.message}") }
        processError(
            AgentServiceError(
                type = AgentServiceErrorType.UNEXPECTED_ERROR,
                message = exception.message ?: "unknown error"
            )
        )
    }

    override suspend fun sendTermination(result: String?) {
        logger.info { formatLog("Sending final result") }
        val message = AgentTerminationToEnvironmentMessage(
            sessionUuid ?: throw IllegalStateException("Session UUID is null"),
            content = AgentToolCallToEnvironmentContent(
                agentId = strategy.name,
                toolCallId = null,
                toolName = TerminationTool.NAME,
                toolArgs = JsonObject(mapOf(TerminationTool.ARG to JsonPrimitive(result)))
            )
        )

        terminate(message)
    }

    //region Private Methods

    private suspend fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        pipeline.install(feature, configure)
    }

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(content: AgentToolCallToEnvironmentContent): EnvironmentToolResultToAgentContent =
        allowToolCalls {
            logger.debug { "Handling tool call sent by server..." }

            val stage = content.toolArgs[ToolStage.STAGE_PARAM_NAME]?.jsonPrimitive?.contentOrNull
                ?.let { stageArg -> toolRegistry.getStageByName(stageArg) }
                // If the tool appears in different stages, the first one will be returned
                ?: toolRegistry.getStageByToolOrNull(content.toolName)
                ?: toolRegistry.getStageByName(ToolStage.DEFAULT_STAGE_NAME)

            // Tool
            val tool = stage.getToolOrNull(content.toolName)
            if (tool == null) {
                logger.warning { "Tool \"${content.toolName}\" not found in stage \"${stage.name}\"!" }
                return toolResult(
                    message = "Tool \"${content.toolName}\" not found!",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            }

            // Tool Args
            val toolArgs = try {
                tool.decodeArgs(content.toolArgs)
            }
            catch (e: Exception) {
                logger.error(e) { "Tool \"${tool.name}\" failed to parse arguments: ${content.toolArgs}" }
                return toolResult(
                    message = "Tool \"${tool.name}\" failed to parse arguments because of ${e.message}!",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            }

            pipeline.onToolCall(stage = stage, tool = tool, toolArgs = toolArgs)

            // Tool Execution
            val (toolResult, serializedResult) = try {
                @Suppress("UNCHECKED_CAST")
                (tool as Tool<Tool.Args, ToolResult>).executeAndSerialize(toolArgs, toolEnabler)
            }
            catch (e: ToolException) {

                pipeline.onToolValidationError(stage = stage, tool = tool, toolArgs = toolArgs, error = e.message)

                return toolResult(
                    message = e.message,
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            }
            catch (e: Exception) {

                logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${content.toolArgs}" }

                pipeline.onToolCallFailure(stage = stage, tool = tool, toolArgs = toolArgs, throwable = e)

                return toolResult(
                    message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            }

            // Tool Finished with Result
            pipeline.onToolCallResult(stage = stage, tool = tool, toolArgs = toolArgs, result = toolResult)

            logger.debug { "[${stage.name}] - Completed execution of ${content.toolName} with result: $toolResult" }

            return toolResult(
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = strategy.name,
                message = serializedResult,
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
            sessionUuid = message.sessionUuid,
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

    private suspend fun terminate(message: AgentTerminationToEnvironmentMessage) {
        val messageContent = message.content
        val messageError = message.error

        if (messageError == null) {
            logger.debug { "Finished execution chain, processing final result..." }
            check(messageContent != null) { NO_CONTENT }

            check(messageContent.toolName == TerminationTool.NAME) { INVALID_TOOL }

            val element = messageContent.toolArgs[TerminationTool.ARG]
            check(element != null) { NO_RESULT }

            val result = element.jsonPrimitive.contentOrNull

            logger.debug { "Final result sent by server: $result" }

            pipeline.onAgentFinished(strategyName = strategy.name, result = result)
            agentResultDeferred.complete(result)
        }
        else {
            processError(messageError)
        }
    }

    private suspend fun processError(error: AgentServiceError) {
        try {
            throw error.asException()
        }
        catch (e: AgentEngineException) {
            logger.error(e) { "Execution exception reported by server!" }
            pipeline.onAgentRunError(strategyName = strategy.name, throwable = e)
        }
    }

    private fun formatLog(message: String): String =
        "$message [${strategy.name}, ${sessionUuid?.text ?: throw IllegalStateException("Session UUID is null")}]"

    //endregion Private Methods
}
