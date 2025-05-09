@file:OptIn(InternalAgentToolsApi::class)

package ai.grazie.code.agents.core.agent

import ai.grazie.code.agents.core.api.AIAgent
import ai.grazie.code.agents.core.event.AgentHandlerContext
import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.exception.AIAgentEngineException
import ai.grazie.code.agents.core.model.AIAgentServiceError
import ai.grazie.code.agents.core.model.agent.AIAgentConfig
import ai.grazie.code.agents.core.model.agent.AIAgentStrategy
import ai.grazie.code.agents.core.model.message.*
import ai.grazie.code.agents.core.tool.tools.TerminationTool
import ai.grazie.code.agents.core.tools.*
import ai.grazie.utils.mpp.LoggerFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private class DirectToolCallsEnablerImpl : DirectToolCallsEnabler

private class AllowDirectToolCallsContext(val toolEnabler: DirectToolCallsEnabler)

private suspend inline fun <T> allowToolCalls(block: suspend AllowDirectToolCallsContext.() -> T) =
    AllowDirectToolCallsContext(DirectToolCallsEnablerImpl()).block()

/**
 * Base class for managing and running an AI agent through its lifecycle, including tool calls, error handling,
 * and termination processes. This class is designed to handle interactions between an AI agent and its environment.
 * This class is **NOT thread-safe**.
 * This runner is designed to be per-agent.
 * State preservation between [run] calls is not guaranteed.
 *
 * @param strategy The agent to be managed and executed.
 * @param toolRegistry A registry managing tools and their associated stages available to the agent.
 * @param eventHandler An event handler that listens for and processes various agent-related events.
 */
abstract class AIAgentBase<TStrategy : AIAgentStrategy<TConfig>, TConfig : AIAgentConfig>(
    protected val strategy: TStrategy,
    val toolRegistry: ToolRegistry,
    private val eventHandler: EventHandler,
    val agentConfig: TConfig,
) : AIAgent {

    private companion object {
        private val logger = LoggerFactory.create("ai.grazie.code.agents.core.${AIAgentBase::class.simpleName!!}")

        private const val INVALID_TOOL = "Can not call tools beside \"${TerminationTool.NAME}\"!"

        private const val NO_CONTENT = "Could not find \"content\", but \"error\" is also absent!"

        private const val NO_RESULT = "Required tool argument value not found: \"${TerminationTool.ARG}\"!"

        private const val CONNECTION_REQUIRED = "Can not send messages without a connection to Agentic Engine!"
    }

    private var isRunning = false
    private val runningMutex = Mutex()

    private val agentResultDeferred: CompletableDeferred<String?> = CompletableDeferred()

    protected abstract suspend fun init(prompt: String): AgentToEnvironmentMessage

    protected abstract suspend fun toolResult(
        toolCallId: String?,
        toolName: String,
        agentId: String,
        message: String,
        result: ToolResult? = null
    ): EnvironmentToolResultToAgentContent

    protected abstract suspend fun sendToAgent(message: EnvironmentToAgentMessage): AgentToEnvironmentMessage

    /**
     * Executes the main agent lifecycle, handling messages between the agent and its environment.
     * This method is allowed to be called several times, but only one execution will be active at a time.
     *
     * @param prompt The initial input string to start the agent execution process.
     */
//    override suspend fun run(prompt: String) {
//        runningMutex.withLock {
//            if (isRunning) throw IllegalStateException("Agent is already running!")
//            isRunning = true
//        }
//
//        var currentMessage: AgentToEnvironmentMessage? = init(prompt)
//        while (isRunning) {
//            when (currentMessage) {
//                is AgentToolCallsToEnvironmentMessage -> {
//                    currentMessage = sendToAgent(processToolCallMultiple(currentMessage))
//                }
//
//                is AgentErrorToEnvironmentMessage -> {
//                    processError(currentMessage.error)
//                    currentMessage = null
//                }
//
//                is AgentTerminationToEnvironmentMessage -> {
//                    terminate(currentMessage)
//                    currentMessage = null
//                }
//
//                null -> {
//                    // If execution is stopped by an error (and we didn't throw an exception up to this point),
//                    // let's complete the deferred with null to unblock any awaiting coroutines.
//                    if (!agentResultDeferred.isCompleted) {
//                        agentResultDeferred.complete(null)
//                    }
//                    logger.debug { "Agent execution completed. Stopping..." }
//                    runningMutex.withLock {
//                        isRunning = false
//                    }
//                }
//            }
//        }
//    }

    /**
     * Runs the agent with the given user input and retrieves the final result after the agent's execution.
     *
     * @param userPrompt The input string used to initialize and run the agent.
     * @return A string containing the result of the agent's execution, or null if the result could not be retrieved.
     */
    override suspend fun runAndGetResult(userPrompt: String): String? {
        run(userPrompt)
        return agentResultDeferred.await()
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
                    agentId = this@AIAgentBase.strategy.name,
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

    protected suspend fun processError(error: AIAgentServiceError) {
        try {
            throw error.asException()
        } catch (e: AIAgentEngineException) {
            logger.error(e) { "Execution exception reported by server!" }

            with(eventHandler.errorHandler) {
                if (!AgentHandlerContext(strategy.name).handle(e)) throw e
            }
        }
    }

    protected suspend fun terminate(message: AgentTerminationToEnvironmentMessage) {
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

    //endregion Private Methods
}
