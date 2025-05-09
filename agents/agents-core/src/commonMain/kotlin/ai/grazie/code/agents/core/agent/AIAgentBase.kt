@file:OptIn(InternalAgentToolsApi::class)

package ai.grazie.code.agents.core.agent

import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.grazie.code.agents.core.api.AIAgent
import ai.grazie.code.agents.core.environment.AgentEnvironment
import ai.grazie.code.agents.core.environment.AgentEnvironmentUtils.mapToToolResult
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.event.AgentHandlerContext
import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.exception.AIAgentEngineException
import ai.grazie.code.agents.core.feature.AIAgentPipeline
import ai.grazie.code.agents.core.feature.KotlinAIAgentFeature
import ai.grazie.code.agents.core.feature.config.FeatureConfig
import ai.grazie.code.agents.core.model.AIAgentServiceError
import ai.grazie.code.agents.core.model.AIAgentServiceErrorType
import ai.grazie.code.agents.core.model.message.*
import ai.grazie.code.agents.core.tool.tools.TerminationTool
import ai.grazie.code.agents.core.tools.*
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

private class DirectToolCallsEnablerImpl : DirectToolCallsEnabler

private class AllowDirectToolCallsContext(val toolEnabler: DirectToolCallsEnabler)

private suspend inline fun <T> allowToolCalls(block: suspend AllowDirectToolCallsContext.() -> T) =
    AllowDirectToolCallsContext(DirectToolCallsEnablerImpl()).block()

open class AIAgentBase(
    val toolRegistry: ToolRegistry = ToolRegistry.Companion.EMPTY,
    private val strategy: LocalAgentStrategy,
    private val eventHandler: EventHandler = EventHandler.Companion.NO_HANDLER,
    val agentConfig: LocalAgentConfig,
    val promptExecutor: PromptExecutor,
    cs: CoroutineScope,
    private val installFeatures: suspend FeatureContext.() -> Unit = {}
) : AIAgent, AgentEnvironment {
    companion object {
        private val logger = LoggerFactory.create("ai.grazie.code.agents.core.agent.${AIAgentBase::class.simpleName}")
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
    class FeatureContext internal constructor(val agent: AIAgentBase) {
        suspend fun <Config : FeatureConfig, Feature : Any> install(
            feature: KotlinAIAgentFeature<Config, Feature>,
            configure: Config.() -> Unit = {}
        ) {
            agent.install(feature, configure)
        }
    }

    private val pipeline = AIAgentPipeline()

    init {
        cs.launch(context = Dispatchers.SuitableForIO, start = CoroutineStart.UNDISPATCHED) {
            FeatureContext(this@AIAgentBase).installFeatures()
            pipeline.onAgentCreated(strategy, this@AIAgentBase)
        }
    }

    private suspend fun <Config : FeatureConfig, Feature : Any> install(
        feature: KotlinAIAgentFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        pipeline.install(feature, configure)
    }

    private var isRunning = false
    private var sessionUuid: UUID? = null
    private val runningMutex = Mutex()
    private val agentResultDeferred: CompletableDeferred<String?> = CompletableDeferred()

    override suspend fun run(prompt: String) {
        runningMutex.withLock {
            if (isRunning) {
                throw IllegalStateException("Agent is already running")
            }

            isRunning = true
            sessionUuid = UUID.random()
        }

        strategy.run(
            sessionUuid = sessionUuid ?: throw IllegalStateException("Session UUID is null"),
            userInput = prompt,
            toolRegistry = toolRegistry,
            promptExecutor = promptExecutor,
            environment = this,
            config = agentConfig,
            pipeline = pipeline
        )

        runningMutex.withLock {
            isRunning = false
            sessionUuid = null
            if (!agentResultDeferred.isCompleted) {
                agentResultDeferred.complete(null)
            }
        }
    }

    override suspend fun runAndGetResult(userPrompt: String): String? {
        run(userPrompt)
        agentResultDeferred.await()
        return agentResultDeferred.getCompleted()
    }

    fun toolResult(
        toolCallId: String?,
        toolName: String,
        agentId: String,
        message: String,
        result: ToolResult?
    ): EnvironmentToolResultToAgentContent = LocalAgentEnvironmentToolResultToAgentContent(
        toolCallId = toolCallId,
        toolName = toolName,
        agentId = agentId,
        message = message,
        toolResult = result
    )

    suspend fun run(builder: suspend TextContentBuilder.() -> Unit) {
        pipeline.awaitFeaturesStreamProvidersReady()

        val prompt = TextContentBuilder().apply { this.builder() }.build()
        run(prompt = prompt)

        pipeline.closeFeaturesStreamProviders()
    }

    private fun formatLog(message: String): String =
        "$message [${strategy.name}, ${sessionUuid?.text ?: throw IllegalStateException("Session UUID is null")}]"

    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        logger.info { formatLog("Executing tools '$toolCalls'") }
        pipeline.onBeforeToolCalls(toolCalls)

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
        pipeline.onAfterToolCalls(results)
        return results
    }

    override suspend fun reportProblem(exception: Throwable) {
        logger.error(exception) { formatLog("Reporting problem: ${exception.message}") }
        processError(
            AIAgentServiceError(
                type = AIAgentServiceErrorType.UNEXPECTED_ERROR,
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

    protected suspend fun processToolCallMultiple(message: AgentToolCallsToEnvironmentMessage): EnvironmentToolResultMultipleToAgentMessage {
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

    private suspend fun processToolCall(content: AgentToolCallToEnvironmentContent): EnvironmentToolResultToAgentContent =
        allowToolCalls {
            logger.debug { "Handling tool call sent by server..." }

            val stage = content.toolArgs[ToolStage.STAGE_PARAM_NAME]?.jsonPrimitive?.contentOrNull
                ?.let { stageArg -> toolRegistry.getStageByName(stageArg) }
                // If the tool appears in different stages, the first one will be returned
                ?: toolRegistry.getStageByToolOrNull(content.toolName)
                ?: toolRegistry.getStageByName(ToolStage.DEFAULT_STAGE_NAME)

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

            val args = try {
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

            try {
                eventHandler.toolCallListener.call(stage, tool, args)
            } catch (e: Exception) {
                logger.error(e) { "Tool \"${tool.name}\" failed to run call handler with arguments: ${content.toolArgs}" }
            }

            val (result, serializedResult) = try {
                @Suppress("UNCHECKED_CAST")
                (tool as Tool<Tool.Args, ToolResult>).executeAndSerialize(args, toolEnabler)
            } catch (e: ToolException) {
                with(eventHandler.toolValidationFailureListener) {
                    AgentHandlerContext(strategy.name).handle(stage, tool, args, e.message)
                }
                return toolResult(
                    message = e.message,
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            } catch (e: Exception) {
                with(eventHandler.toolExceptionListener) {
                    AgentHandlerContext(strategy.name).handle(stage, tool, args, e)
                }
                logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${content.toolArgs}" }
                return toolResult(
                    message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            }

            try {
                eventHandler.toolResultListener.result(stage, tool, args, result)
            } catch (e: Exception) {
                logger.error(e) { "Tool \"${tool.name}\" failed to run result handler with arguments: ${content.toolArgs}" }
            }

            logger.debug { "[${stage.name}] - Completed execution of ${content.toolName} with result: $result" }

            return toolResult(
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = strategy.name,
                message = serializedResult,
                result = result
            )
        }

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
            with(eventHandler.resultHandler) {
                AgentHandlerContext(strategy.name).handle(result)
            }
            agentResultDeferred.complete(result)
        } else {
            processError(messageError)
        }
    }

    private suspend fun processError(error: AIAgentServiceError) {
        try {
            throw error.asException()
        } catch (e: AIAgentEngineException) {
            logger.error(e) { "Execution exception reported by server!" }

            with(eventHandler.errorHandler) {
                if (!AgentHandlerContext(strategy.name).handle(e)) throw e
            }
        }
    }
}