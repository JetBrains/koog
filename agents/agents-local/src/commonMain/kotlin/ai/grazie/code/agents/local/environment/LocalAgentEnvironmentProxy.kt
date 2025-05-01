package ai.grazie.code.agents.local.environment

import ai.grazie.code.agents.core.TerminationTool
import ai.grazie.code.agents.core.model.AIAgentServiceError
import ai.grazie.code.agents.core.model.AIAgentServiceErrorType
import ai.grazie.code.agents.core.model.message.*
import ai.grazie.code.agents.local.agent.AgentTerminationByClientException
import ai.grazie.code.agents.local.agent.LocalAgentStrategy
import ai.grazie.code.agents.local.engine.UnexpectedAgentMessageException
import ai.grazie.code.agents.local.engine.UnexpectedDoubleInitializationException
import ai.grazie.code.agents.local.features.AIAgentPipeline
import ai.grazie.code.agents.local.model.message.LocalAgentEnvironmentToAgentInitializeMessage
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Performs proxying of the requests
 */
class LocalAgentEnvironmentProxy(
    val engine: SendChannel<AgentToEnvironmentMessage>,
    val environment: ReceiveChannel<EnvironmentToAgentMessage>,
    val sessionUuid: UUID,
    val strategy: LocalAgentStrategy,
    internal val pipeline: AIAgentPipeline
) : AgentEnvironment {

    companion object {
        private val logger =
            LoggerFactory.create("ai.grazie.code.agents.local.environment.${LocalAgentEnvironmentProxy::class.simpleName}")
    }

    private fun formatLog(message: String): String = "$message [${strategy.name}, ${sessionUuid.text}]"

    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        logger.info { formatLog("Executing tools '$toolCalls'") }
        pipeline.onBeforeToolCalls(toolCalls)

        engine.send(
            AgentToolCallMultipleToEnvironmentMessage(
                sessionUuid = sessionUuid,
                content = toolCalls.map { call ->
                    AgentToolCallToEnvironmentContent(
                        agentId = strategy.name,
                        toolCallId = call.id,
                        toolName = call.tool,
                        toolArgs = call.contentJson
                    )
                }
            )
        )

        val response = environment.receive()
        logger.info { formatLog("Received responses for tools: ${response::class.simpleName}") }

        val toolCallResults = processResult(response)
        logger.info { formatLog("Successful tool results: $toolCallResults") }
        pipeline.onAfterToolCalls(toolCallResults)

        return toolCallResults
    }

    override suspend fun reportProblem(exception: Throwable) {
        logger.error(exception) { formatLog("Reporting problem: ${exception.message}") }
        engine.send(
            AgentErrorToEnvironmentMessage(
                sessionUuid,
                AIAgentServiceError(
                    type = AIAgentServiceErrorType.UNEXPECTED_ERROR,
                    message = exception.message ?: "unknown error"
                )
            )
        )
    }

    override suspend fun sendTermination(result: String?) {
        logger.info { formatLog("Sending final result") }
        engine.send(
            AgentTerminationToEnvironmentMessage(
                sessionUuid,
                content = AgentToolCallToEnvironmentContent(
                    agentId = strategy.name,
                    toolCallId = null,
                    toolName = TerminationTool.NAME,
                    toolArgs = JsonObject(mapOf(TerminationTool.ARG to JsonPrimitive(result)))
                )
            )
        )
    }

    private fun processResult(response: EnvironmentToAgentMessage): List<ReceivedToolResult> {
        return when (response) {
            is EnvironmentToolResultSingleToAgentMessage -> {
                listOf(response.content.toResult())
            }

            is EnvironmentToolResultMultipleToAgentMessage -> {
                response.content.map { it.toResult() }
            }

            is EnvironmentToAgentErrorMessage -> {
                logger.error { formatLog("Error while executing tools: ${response.error.message}") }
                throw AgentTerminationByClientException(response.error.message)
            }

            is EnvironmentToAgentTerminationMessage -> {
                logger.info { formatLog("Received termination signal") }
                throw AgentTerminationByClientException(
                    response.content?.message ?: response.error?.message ?: ""
                )
            }

            is LocalAgentEnvironmentToAgentInitializeMessage -> {
                logger.error { formatLog("Unexpected double initialization") }
                throw UnexpectedDoubleInitializationException()
            }

            else -> {
                logger.error { formatLog("Unexpected message type ${response::class.simpleName}") }
                throw UnexpectedAgentMessageException()
            }
        }
    }

}
