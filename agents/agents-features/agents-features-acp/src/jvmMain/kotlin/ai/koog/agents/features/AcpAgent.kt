package ai.koog.agents.features

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
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow

public class AcpAgent {
    public class AcpConfig : FeatureConfig() {
        public lateinit var eventsFlow: MutableSharedFlow<Event>
    }

    public companion object Feature :
        AIAgentGraphFeature<AcpConfig, AcpAgent>,
        AIAgentFunctionalFeature<AcpConfig, AcpAgent> {

        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<AcpAgent> = AIAgentStorageKey("agents-features-acp")

        override fun createInitialConfig(): AcpConfig = AcpConfig()

        override fun install(
            config: AcpConfig,
            pipeline: AIAgentGraphPipeline,
        ): AcpAgent {
            logger.info { "Start installing feature: ${AcpAgent::class.simpleName}" }

            val acpAgent = AcpAgent()
            config.eventsFlow.registerCommonPipelineHandlers(pipeline)

            return acpAgent
        }

        override fun install(
            config: AcpConfig,
            pipeline: AIAgentFunctionalPipeline,
        ): AcpAgent {
            logger.info { "Start installing feature: ${AcpAgent::class.simpleName}" }

            val acpAgent = AcpAgent()
            config.eventsFlow.registerCommonPipelineHandlers(pipeline)

            return acpAgent
        }

        private fun MutableSharedFlow<Event>.registerCommonPipelineHandlers(
            pipeline: AIAgentPipeline,
        ) {
            pipeline.interceptAgentCompleted(this@Feature) intercept@{ eventContext ->
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
                                    is ContentPart.Text -> emit(
                                        Event.SessionUpdateEvent(
                                            update = SessionUpdate.AgentMessageChunk(
                                                content = ContentBlock.Text(part.text)
                                            )
                                        )
                                    )

                                    else -> TODO("Implement other content parts")
                                }
                            }
                        }

                        is Message.Reasoning -> {
                            emit(
                                Event.SessionUpdateEvent(
                                    update = SessionUpdate.AgentThoughtChunk(
                                        content = ContentBlock.Text(it.content)
                                    )
                                )
                            )
                        }

                        is Message.Tool.Call -> {
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
