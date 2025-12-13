package ai.koog.agents.features.acp

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.AcpMethod
import com.agentclientprotocol.model.AcpNotification
import com.agentclientprotocol.model.AcpRequest
import com.agentclientprotocol.model.AcpResponse
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.protocol.sendNotification
import com.agentclientprotocol.protocol.sendRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.FlowCollector

/**
 * AcpAgent is the main class for interacting with the Agent Client Protocol.
 *
 * @property sessionId The session ID of the ACP agent.
 * @property protocol The protocol instance to use for sending requests and notifications to ACP Client.
 * @property eventsFlow The flow of the notification events emitted by the agent.
 *
 * This feature is accessible from Koog Agent and allows sending requests and notifications to the ACP Client
 * via [sendRequest] and [sendNotification] methods.
 * Notification can be handled automatically buy default and can be configured via [AcpConfig.setDefaultNotifications]
 */
public class AcpAgent(
    public val sessionId: SessionId,
    public val protocol: Protocol,
    public val eventsFlow: FlowCollector<Event>,
) {

    /**
     * Configuration for the ACP Agent feature.
     *
     * @property sessionIdValue The session ID of the ACP agent.
     * @property protocol The protocol instance to use for sending requests and notifications to ACP Client.
     * @property eventsFlow The flow of the notification events emitted by the agent.
     * @property setDefaultNotifications Whether to register default notification handlers for the agent.
     */
    public class AcpConfig : FeatureConfig() {
        // TODO: Can not use SessionId because inline, ask SDK team
        public lateinit var sessionIdValue: String
        public lateinit var protocol: Protocol
        public lateinit var eventsFlow: FlowCollector<Event>
        public var setDefaultNotifications: Boolean = true
    }

    /**
     * Sends a request to the ACP protocol and receive the corresponding response.
     *
     * @param method The method describing the request-response interaction via [com.agentclientprotocol.model.AcpMethod.AcpRequestResponseMethod].
     * @param request The request object of type [TRequest] to be sent. Defaults to `null`.
     * @return The response of type [TResponse] received from the ACP protocol.
     */
    public suspend inline fun <reified TRequest : AcpRequest, reified TResponse : AcpResponse> sendRequest(
        method: AcpMethod.AcpRequestResponseMethod<TRequest, TResponse>,
        request: TRequest? = null,
    ): TResponse {
        return protocol.sendRequest(method, request)
    }

    /**
     * Sends a notification to the ACP protocol.
     *
     * @param method The notification method describing the interaction with the ACP protocol.
     *               Must be of type [AcpMethod.AcpNotificationMethod] specific to [TNotification].
     * @param notification The notification object of type [TNotification] to be sent. Defaults to `null`.
     */
    public inline fun <reified TNotification : AcpNotification> sendNotification(
        method: AcpMethod.AcpNotificationMethod<TNotification>,
        notification: TNotification? = null,
    ) {
        return protocol.sendNotification(method, notification)
    }

    public companion object Feature :
        AIAgentGraphFeature<AcpConfig, AcpAgent>,
        AIAgentFunctionalFeature<AcpConfig, AcpAgent> {

        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<AcpAgent> = AIAgentStorageKey("agents-features-acp")

        override fun createInitialConfig(): AcpConfig = AcpConfig()

        private fun createFeature(
            config: AcpConfig,
        ): AcpAgent {
            logger.info { "Start installing feature: ${AcpAgent::class.simpleName}" }

            val acpAgent = AcpAgent(
                SessionId(value = config.sessionIdValue),
                config.protocol,
                config.eventsFlow
            )

            return acpAgent
        }

        override fun install(
            config: AcpConfig,
            pipeline: AIAgentGraphPipeline,
        ): AcpAgent {
            logger.info { "Start installing feature: ${AcpAgent::class.simpleName}" }

            val acpAgent = createFeature(config)
            if (config.setDefaultNotifications) {
                config.eventsFlow.registerDefaultNotificationHandlers(pipeline)
            }

            return acpAgent
        }

        override fun install(
            config: AcpConfig,
            pipeline: AIAgentFunctionalPipeline,
        ): AcpAgent {
            logger.info { "Start installing feature: ${AcpAgent::class.simpleName}" }

            val acpAgent = createFeature(config)
            if (config.setDefaultNotifications) {
                config.eventsFlow.registerDefaultNotificationHandlers(pipeline)
            }

            return acpAgent
        }

        private fun FlowCollector<Event>.registerDefaultNotificationHandlers(
            pipeline: AIAgentPipeline,
        ) {
            pipeline.interceptAgentCompleted(this@Feature) intercept@{ eventContext ->
                logger.info { "Emitting PromptResponseEvent with StopReason.END_TURN" }
                emit(
                    Event.PromptResponseEvent(
                        response = PromptResponse(
                            stopReason = StopReason.END_TURN
                        )
                    )
                )
            }

            pipeline.interceptAgentExecutionFailed(this@Feature) intercept@{ eventContext ->
                // TODO: Analyze the exception and emit appropriate event
                eventContext.throwable
                logger.info { "Emitting PromptResponseEvent with StopReason.REFUSAL" }

                emit(
                    Event.PromptResponseEvent(
                        response = PromptResponse(
                            stopReason = StopReason.REFUSAL
                        )
                    )
                )
            }

            pipeline.interceptLLMCallCompleted(this@Feature) intercept@{ eventContext: LLMCallCompletedContext ->
                eventContext.responses.forEach {
                    when (it) {
                        is Message.Assistant -> {
                            it.parts.forEach { part ->
                                when (part) {
                                    is ContentPart.Text -> {
                                        logger.info { "Emitting SessionUpdateEvent for Assistant message chunk" }
                                        emit(
                                            Event.SessionUpdateEvent(
                                                update = SessionUpdate.AgentMessageChunk(
                                                    content = ContentBlock.Text(part.text)
                                                )
                                            )
                                        )
                                    }

                                    else -> TODO("Implement other content parts")
                                }
                            }
                        }

                        is Message.Reasoning -> {
                            logger.info { "Emitting AgentThoughtChunk event for Reasoning message chunk" }
                            emit(
                                Event.SessionUpdateEvent(
                                    update = SessionUpdate.AgentThoughtChunk(
                                        content = ContentBlock.Text(it.content)
                                    )
                                )
                            )
                        }

                        is Message.Tool.Call -> {
                            logger.info { "Emitting SessionUpdateEvent for ToolCall" }
                            emit(
                                Event.SessionUpdateEvent(
                                    update = SessionUpdate.ToolCall(
                                        toolCallId = ToolCallId(it.id ?: "unknown"),
                                        // TODO: Support tool description in the event
                                        title = it.tool,
                                        // TODO: Support kind for tools
                                        status = ToolCallStatus.PENDING,
                                        rawInput = it.contentJson,
                                    )
                                )
                            )
                        }
                    }
                }
            }

            pipeline.interceptToolCallStarting(this@Feature) intercept@{ eventContext ->
                logger.info { "Emitting SessionUpdateEvent for ToolCall Starting" }
                emit(
                    Event.SessionUpdateEvent(
                        update = SessionUpdate.ToolCall(
                            toolCallId = ToolCallId(eventContext.toolCallId ?: "unknown"),
                            title = eventContext.tool.description,
                            // TODO: Support kind for tools
                            status = ToolCallStatus.IN_PROGRESS,
                            rawInput = eventContext.tool.encodeArgsUnsafe(eventContext.toolArgs),
                        )
                    )
                )
            }

            pipeline.interceptToolCallFailed(this@Feature) intercept@{ eventContext ->
                logger.info { "Emitting SessionUpdateEvent for ToolCall Failed" }
                emit(
                    Event.SessionUpdateEvent(
                        update = SessionUpdate.ToolCall(
                            toolCallId = ToolCallId(eventContext.toolCallId ?: "unknown"),
                            title = eventContext.tool.description,
                            // TODO: Support kind for tools
                            status = ToolCallStatus.FAILED,
                            rawInput = eventContext.tool.encodeArgsUnsafe(eventContext.toolArgs),
                        )
                    )
                )
            }

            @OptIn(InternalAgentToolsApi::class)
            pipeline.interceptToolCallCompleted(this@Feature) intercept@{ eventContext: ToolCallCompletedContext ->
                logger.info { "Emitting SessionUpdateEvent for ToolCall Completed" }
                emit(
                    Event.SessionUpdateEvent(
                        update = SessionUpdate.ToolCall(
                            toolCallId = ToolCallId(eventContext.toolCallId ?: "unknown"),
                            title = eventContext.tool.description,
                            // TODO: Support kind for tools
                            status = ToolCallStatus.COMPLETED,
                            rawInput = eventContext.tool.encodeArgsUnsafe(eventContext.toolArgs),
                            rawOutput = eventContext.tool.encodeResultUnsafe(eventContext.result)
                        )
                    )
                )
            }
        }
    }
}
