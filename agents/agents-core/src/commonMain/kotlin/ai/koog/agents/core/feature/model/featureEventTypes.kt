package ai.koog.agents.core.feature.model

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.local.features.common.message.FeatureEvent
import ai.koog.agents.local.features.common.message.FeatureMessage
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
public sealed class DefinedFeatureEvent() : FeatureEvent {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}

//region Agent

@Serializable
public data class AIAgentStartedEvent(
    val strategyName: String,
    override val eventId: String = AIAgentStartedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class AIAgentFinishedEvent(
    val strategyName: String,
    val result: String?,
    override val eventId: String = AIAgentFinishedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class AIAgentRunErrorEvent(
    val strategyName: String,
    val error: AIAgentError,
    override val eventId: String = AIAgentRunErrorEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Agent

//region Strategy

@Serializable
public data class AIAgentStrategyStartEvent(
    val strategyName: String,
    override val eventId: String = AIAgentStrategyStartEvent::class.simpleName!!
) : DefinedFeatureEvent()

@Serializable
public data class AIAgentStrategyFinishedEvent(
    val strategyName: String,
    val result: String,
    override val eventId: String = AIAgentStrategyFinishedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Strategy

//region Node

@Serializable
public data class AIAgentNodeExecutionStartEvent(
    val nodeName: String,
    val input: String,
    override val eventId: String = AIAgentNodeExecutionStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class AIAgentNodeExecutionEndEvent(
    val nodeName: String,
    val input: String,
    val output: String,
    override val eventId: String = AIAgentNodeExecutionEndEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Node

//region LLM Call

@Serializable
public data class LLMCallStartEvent(
    val prompt: String,
    override val eventId: String = LLMCallStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class LLMCallWithToolsStartEvent(
    val prompt: String,
    val tools: List<String>,
    override val eventId: String = LLMCallWithToolsStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class LLMCallEndEvent(
    val response: String,
    override val eventId: String = LLMCallEndEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class LLMCallWithToolsEndEvent(
    val responses: List<String>,
    val tools: List<String>,
    override val eventId: String = LLMCallWithToolsEndEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion LLM Call

//region Tool Call

@Serializable
public data class ToolCallEvent(
    val toolName: String,
    val toolArgs: Tool.Args,
    override val eventId: String = ToolCallEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class ToolValidationErrorEvent(
    val toolName: String,
    val toolArgs: Tool.Args,
    val errorMessage: String,
    override val eventId: String = ToolValidationErrorEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class ToolCallFailureEvent(
    val toolName: String,
    val toolArgs: Tool.Args,
    val error: AIAgentError,
    override val eventId: String = ToolCallFailureEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class ToolCallResultEvent(
    val toolName: String,
    val toolArgs: Tool.Args,
    val result: ToolResult?,
    override val eventId: String = ToolCallResultEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Tool Call
