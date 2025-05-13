package ai.grazie.code.agents.core.feature.model

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.local.features.common.message.FeatureEvent
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
public sealed class DefinedFeatureEvent() : FeatureEvent {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}

//region Agent

@Serializable
public data class AgentCreateEvent(
    val strategyName: String,
    override val eventId: String = AgentCreateEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class AgentStartedEvent(
    val strategyName: String,
    override val eventId: String = AgentStartedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class AgentFinishedEvent(
    val strategyName: String,
    val result: String?,
    override val eventId: String = AgentFinishedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class AgentRunErrorEvent(
    val strategyName: String,
    val error: AgentError,
    override val eventId: String = AgentRunErrorEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Agent

//region Strategy

@Serializable
public data class StrategyStartEvent(
    val strategyName: String,
    override val eventId: String = StrategyStartEvent::class.simpleName!!
) : DefinedFeatureEvent()

@Serializable
public data class StrategyFinishedEvent(
    val strategyName: String,
    val result: String,
    override val eventId: String = StrategyFinishedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Strategy

//region Node

@Serializable
public data class NodeExecutionStartEvent(
    val nodeName: String,
    val stageName: String,
    val input: String,
    override val eventId: String = NodeExecutionStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class NodeExecutionEndEvent(
    val nodeName: String,
    val stageName: String,
    val input: String,
    val output: String,
    override val eventId: String = NodeExecutionEndEvent::class.simpleName!!,
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
    val stageName: String,
    val toolName: String,
    val toolArgs: Tool.Args,
    override val eventId: String = ToolCallEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class ToolValidationErrorEvent(
    val stageName: String,
    val toolName: String,
    val toolArgs: Tool.Args,
    val errorMessage: String,
    override val eventId: String = ToolValidationErrorEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class ToolCallFailureEvent(
    val stageName: String,
    val toolName: String,
    val toolArgs: Tool.Args,
    val error: AgentError,
    override val eventId: String = ToolCallFailureEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
public data class ToolCallResultEvent(
    val stageName: String,
    val toolName: String,
    val toolArgs: Tool.Args,
    val result: ToolResult?,
    override val eventId: String = ToolCallResultEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Tool Call
