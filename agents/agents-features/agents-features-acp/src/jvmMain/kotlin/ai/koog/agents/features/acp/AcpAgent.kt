package ai.koog.agents.features.acp

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.featureOrThrow
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.protocol.Protocol
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.ProducerScope
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * [AcpAgent] is the main class for interacting with the Agent Client Protocol.
 * https://agentclientprotocol.com/
 * This feature allows sending requests and notifications to the ACP Client via [sendEvent] or [protocol]
 * Notification can be handled automatically by default and can be configured via [AcpConfig.setDefaultNotifications]
 *
 * @property sessionId The session ID of the ACP agent.
 * @property protocol The protocol instance to use for sending requests and notifications to ACP Client.
 * @param eventsProducer A coroutine-based producer scope for sending [Event] instances.
 */
public class AcpAgent(
    public val sessionId: SessionId,
    public val protocol: Protocol,
    private val eventsProducer: ProducerScope<Event>,
) {
    /**
     * Configuration for the ACP Agent feature.
     */
    public class AcpConfig : FeatureConfig() {
        /**
         * The session ID of the ACP agent.
         */
        public lateinit var sessionId: String

        /**
         * The protocol instance to use for sending requests and notifications to ACP Client.
         */
        public lateinit var protocol: Protocol

        /**
         * A coroutine-based producer scope for sending [Event] instances within the ACP Agent feature.
         * This property is used to facilitate communication of events from the agent to the client.
         */
        public lateinit var eventsProducer: ProducerScope<Event>

        /**
         * Whether to register default notification handlers for the agent.
         */
        public var setDefaultNotifications: Boolean = true
    }

    /**
     * Sends [Event] to the connected ACP client.
     *
     * @param event The event to send.
     */
    public suspend fun sendEvent(event: Event) {
        eventsProducer.send(event)
    }

    /**
     * Companion object, handling [AcpAgent] feature creation and installation.
     *
     * Example configuration within the [com.agentclientprotocol.agent.AgentSession] implementation:
     * ```kotlin
     * class KoogAgentSession(
     *     override val sessionId: SessionId,
     *     private val promptExecutor: PromptExecutor,
     *     private val protocol: Protocol,
     *     private val clock: Clock,
     *     // other parameters...
     * ) : AgentSession {
     *   override suspend fun prompt(
     *       content: List<ContentBlock>,
     *       _meta: JsonElement?
     *   ): Flow<Event> = channelFlow {
     *     val agent = AIAgent(
     *       // agent configuration
     *     ) {
     *      install(AcpAgent) {
     *        this.sessionId = this@KoogAgentSession.sessionId.value
     *        this.protocol = this@KoogAgentSession.protocol
     *        this.eventsProducer = this@channelFlow
     *        this.setDefaultNotifications = true
     *      }
     *       // other features...
     *     }
     *
     *     agent.run(/* input */)
     *
     *   }
     * }
     * ```
     */
    public companion object Feature :
        AIAgentGraphFeature<AcpConfig, AcpAgent>,
        AIAgentFunctionalFeature<AcpConfig, AcpAgent> {

        private val logger = KotlinLogging.logger { }
        override val key: AIAgentStorageKey<AcpAgent> = AIAgentStorageKey("agents-features-acp")

        override fun createInitialConfig(): AcpConfig = AcpConfig()

        private fun createFeature(
            config: AcpConfig,
        ): AcpAgent {
            logger.debug { "Start installing feature: ${AcpAgent::class.simpleName}" }

            val acpAgent = AcpAgent(
                sessionId = SessionId(value = config.sessionId),
                protocol = config.protocol,
                eventsProducer = config.eventsProducer
            )

            return acpAgent
        }

        override fun install(
            config: AcpConfig,
            pipeline: AIAgentGraphPipeline,
        ): AcpAgent {
            logger.debug { "Start installing feature: ${AcpAgent::class.simpleName}" }

            val acpAgent = createFeature(config)
            if (config.setDefaultNotifications) {
                acpAgent.registerDefaultNotificationHandlers(pipeline)
            }

            return acpAgent
        }

        override fun install(
            config: AcpConfig,
            pipeline: AIAgentFunctionalPipeline,
        ): AcpAgent {
            logger.debug { "Start installing feature: ${AcpAgent::class.simpleName}" }

            val acpAgent = createFeature(config)
            if (config.setDefaultNotifications) {
                acpAgent.registerDefaultNotificationHandlers(pipeline)
            }

            return acpAgent
        }

        private fun AcpAgent.registerDefaultNotificationHandlers(
            pipeline: AIAgentPipeline,
        ) {
            pipeline.interceptAgentCompleted(this@Feature) {
                logger.debug { "Emitting PromptResponseEvent with StopReason.END_TURN" }
                sendEvent(
                    Event.PromptResponseEvent(
                        response = PromptResponse(
                            stopReason = StopReason.END_TURN
                        )
                    )
                )
            }

            pipeline.interceptAgentExecutionFailed(this@Feature) { ctx ->
                when (ctx.throwable) {
                    is AIAgentMaxNumberOfIterationsReachedException -> {
                        logger.debug { "Emitting PromptResponseEvent with StopReason.MAX_TURN_REQUESTS" }
                        sendEvent(
                            Event.PromptResponseEvent(
                                response = PromptResponse(
                                    stopReason = StopReason.MAX_TURN_REQUESTS
                                )
                            )
                        )
                    }

                    else -> {
                        logger.debug { "Emitting PromptResponseEvent with StopReason.REFUSAL" }
                        sendEvent(
                            Event.PromptResponseEvent(
                                response = PromptResponse(
                                    stopReason = StopReason.REFUSAL
                                )
                            )
                        )
                    }
                }
            }

            pipeline.interceptLLMCallCompleted(this@Feature) { ctx ->
                ctx.responses.forEach {
                    it.toAcpEvents(ctx.tools).forEach { event ->
                        logger.debug { "Emitting event $event for LLM Call Completed" }
                        sendEvent(event)
                    }
                }
            }

            pipeline.interceptToolCallStarting(this@Feature) { ctx ->
                logger.debug { "Emitting SessionUpdateEvent for ToolCall Starting" }
                sendEvent(
                    Event.SessionUpdateEvent(
                        update = SessionUpdate.ToolCall(
                            toolCallId = ToolCallId(ctx.toolCallId ?: UNKNOWN_TOOL_CALL_ID),
                            title = ctx.toolDescription ?: UNKNOWN_TOOL_DESCRIPTION,
                            // TODO: Support kind for tools
                            status = ToolCallStatus.IN_PROGRESS,
                            rawInput = ctx.toolArgs,
                        )
                    )
                )
            }

            pipeline.interceptToolCallFailed(this@Feature) { ctx ->
                logger.debug { "Emitting SessionUpdateEvent for ToolCall Failed" }
                sendEvent(
                    Event.SessionUpdateEvent(
                        update = SessionUpdate.ToolCallUpdate(
                            toolCallId = ToolCallId(ctx.toolCallId ?: UNKNOWN_TOOL_CALL_ID),
                            title = ctx.toolDescription ?: UNKNOWN_TOOL_DESCRIPTION,
                            // TODO: Support kind for tools
                            status = ToolCallStatus.FAILED,
                            rawInput = ctx.toolArgs,
                        )
                    )
                )
            }

            @OptIn(InternalAgentToolsApi::class) pipeline.interceptToolCallCompleted(this@Feature) { ctx ->
                logger.debug { "Emitting SessionUpdateEvent for ToolCall Completed" }
                sendEvent(
                    Event.SessionUpdateEvent(
                        update = SessionUpdate.ToolCallUpdate(
                            toolCallId = ToolCallId(ctx.toolCallId ?: UNKNOWN_TOOL_CALL_ID),
                            title = ctx.toolDescription ?: UNKNOWN_TOOL_DESCRIPTION,
                            // TODO: Support kind for tools
                            status = ToolCallStatus.COMPLETED,
                            rawInput = ctx.toolArgs,
                            rawOutput = ctx.toolResult
                        )
                    )
                )
            }
        }
    }
}

/**
 * Retrieves the [AcpAgent] feature from the agent context.
 *
 * @return The installed [AcpAgent] feature
 * @throws IllegalStateException if the feature is not installed
 */
public fun AIAgentContext.acpAgent(): AcpAgent = featureOrThrow(AcpAgent.Feature)

/**
 * Executes an action with the [AcpAgent] feature as the receiver.
 * This is a convenience function that retrieves the feature and provides it as the receiver for the action block.
 *
 * @param action The action to execute
 * @return The result of the action
 * @throws IllegalStateException if the feature is not installed
 */
@OptIn(ExperimentalContracts::class)
public inline fun <T> AIAgentContext.withAcpAgent(action: AcpAgent.() -> T): T {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }

    return acpAgent().action()
}
