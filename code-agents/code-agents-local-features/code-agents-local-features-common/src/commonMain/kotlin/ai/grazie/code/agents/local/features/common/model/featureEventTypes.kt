package ai.grazie.code.agents.local.features.common.model

import ai.grazie.code.agents.local.features.message.FeatureEvent
import ai.grazie.code.agents.local.features.message.FeatureMessage
import ai.jetbrains.code.prompt.message.Message
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
sealed class DefinedFeatureEvent() : FeatureEvent {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}

//region Strategy

@Serializable
data class AgentCreateEvent(
    val strategyName: String,
    override val eventId: String = AgentCreateEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class StrategyStartEvent(
    val strategyName: String,
    override val eventId: String = StrategyStartEvent::class.simpleName!!
) : DefinedFeatureEvent()

//endregion Strategy

//region Node

@Serializable
data class NodeExecutionStartEvent(
    val nodeName: String,
    val stageName: String,
    val input: String,
    override val eventId: String = NodeExecutionStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class NodeExecutionEndEvent(
    val nodeName: String,
    val stageName: String,
    val input: String,
    val output: String,
    override val eventId: String = NodeExecutionEndEvent::class.simpleName!!,
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
data class ToolCallsStartEvent(
    val tools: List<Message.Tool.Call>,
    override val eventId: String = ToolCallsStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

@Serializable
data class ToolCallsEndEvent(
    val results: List<Message.Tool.Result>,
    override val eventId: String = ToolCallsEndEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Tool Call
