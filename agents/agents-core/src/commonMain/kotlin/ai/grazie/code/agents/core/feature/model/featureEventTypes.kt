package ai.grazie.code.agents.core.feature.model

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.features.common.message.FeatureEvent
import ai.grazie.code.agents.features.common.message.FeatureMessage
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
sealed class DefinedFeatureEvent() : FeatureEvent {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}

//region Agent

@Serializable
data class AIAgentCreateEvent(
    val strategyName: String,
    override val eventId: String = AIAgentCreateEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class AIAgentStartedEvent(
    val strategyName: String,
    override val eventId: String = AIAgentStartedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class AIAgentFinishedEvent(
    val strategyName: String,
    val result: String?,
    override val eventId: String = AIAgentFinishedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class AIAgentRunErrorEvent(
    val strategyName: String,
    val error: AIAgentError,
    override val eventId: String = AIAgentRunErrorEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Agent

//region Strategy

@Serializable
data class AIAgentStrategyStartEvent(
    val strategyName: String,
    override val eventId: String = AIAgentStrategyStartEvent::class.simpleName!!
) : DefinedFeatureEvent()

@Serializable
data class AIAgentStrategyFinishedEvent(
    val strategyName: String,
    val result: String,
    override val eventId: String = AIAgentStrategyFinishedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Strategy

//region Node

@Serializable
data class AIAgentNodeExecutionStartEvent(
    val nodeName: String,
    val stageName: String,
    val input: String,
    override val eventId: String = AIAgentNodeExecutionStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class AIAgentNodeExecutionEndEvent(
    val nodeName: String,
    val stageName: String,
    val input: String,
    val output: String,
    override val eventId: String = AIAgentNodeExecutionEndEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Node

//region LLM Call

@Serializable
data class LLMCallStartEvent(
    val prompt: String,
    override val eventId: String = LLMCallStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class LLMCallWithToolsStartEvent(
    val prompt: String,
    val tools: List<String>,
    override val eventId: String = LLMCallWithToolsStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class LLMCallEndEvent(
    val response: String,
    override val eventId: String = LLMCallEndEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class LLMCallWithToolsEndEvent(
    val responses: List<String>,
    val tools: List<String>,
    override val eventId: String = LLMCallWithToolsEndEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion LLM Call

//region Tool Call

@Serializable
data class ToolCallEvent(
    val stageName: String,
    val toolName: String,
    val toolArgs: Tool.Args,
    override val eventId: String = ToolCallEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class ToolValidationErrorEvent(
    val stageName: String,
    val toolName: String,
    val toolArgs: Tool.Args,
    val errorMessage: String,
    override val eventId: String = ToolValidationErrorEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class ToolCallFailureEvent(
    val stageName: String,
    val toolName: String,
    val toolArgs: Tool.Args,
    val error: AIAgentError,
    override val eventId: String = ToolCallFailureEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class ToolCallResultEvent(
    val stageName: String,
    val toolName: String,
    val toolArgs: Tool.Args,
    val result: ToolResult?,
    override val eventId: String = ToolCallResultEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Tool Call
