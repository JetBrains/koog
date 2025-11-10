package ai.koog.agents.core.feature.remote

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.FeatureEventMessage
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.DefinedFeatureEvent
import ai.koog.agents.core.feature.model.events.FunctionalStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.GraphStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFailedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFrameReceivedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.StrategyStartingEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallFailedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.feature.model.events.ToolValidationFailedEvent
import io.ktor.utils.io.InternalAPI
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlin.reflect.KClass

/**
 * Provides a preconfigured instance of [Json] with specific settings tailored
 * for serializing and deserializing feature messages in a remote communication context.
 *
 * This configuration includes features such as
 * - Enabling pretty printing of JSON for readability.
 * - Ignoring unknown keys during deserialization to support backward and forward compatibility.
 * - Encoding default values to ensure complete serialization of data.
 * - Allowing lenient parsing for more flexible input handling.
 * - Disabling explicit null representation to omit `null` fields when serializing.
 *
 * Additionally, this [Json] instance is configured with a default serializers module,
 * facilitating custom serialization logic for feature messages.
 */
public val defaultFeatureMessageJsonConfig: Json
    get() = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
        serializersModule = defaultFeatureMessageSerializersModule
    }

/**
 * Provides a [SerializersModule] that handles polymorphic serialization and deserialization for various events
 * and messages associated with features, agents, and strategies.
 *
 * This module supports polymorphic serialization for the following base classes:
 * - [FeatureMessage]
 * - [FeatureEvent]
 * - [DefinedFeatureEvent]
 *
 * It registers the concrete subclasses of these base classes for serialization and deserialization:
 * - [AgentStartingEvent] - Fired when an AI agent starts execution
 * - [AgentCompletedEvent] - Fired when an AI agent completes execution
 * - [AgentClosingEvent] - Fired before an AI agent is closed
 * - [AgentExecutionFailedEvent] - Fired when an AI agent encounters a runtime error
 * - [StrategyStartingEvent] - Fired when an AI agent strategy begins
 * - [StrategyCompletedEvent] - Fired when an AI agent strategy completes
 * - [NodeExecutionStartingEvent] - Fired when a node execution starts
 * - [NodeExecutionCompletedEvent] - Fired when a node execution ends
 * - [ToolCallStartingEvent] - Fired when a tool is called
 * - [ToolValidationFailedEvent] - Fired when tool validation fails
 * - [ToolCallFailedEvent] - Fired when a tool call fails
 * - [ToolCallCompletedEvent] - Fired when a tool call returns a result
 * - [LLMCallStartingEvent] - Fired before making an LLM call
 * - [LLMCallCompletedEvent] - Fired after completing an LLM call
 *
 * This configuration enables proper handling of the diverse event types encountered in the system by ensuring
 * that the polymorphic serialization framework can correctly serialize and deserialize each subclass.
 */
public val defaultFeatureMessageSerializersModule: SerializersModule
    get() = SerializersModule {

        polymorphic(FeatureMessage::class) {
            subclass(FeatureStringMessage::class, FeatureStringMessage.serializer())
            subclass(FeatureEventMessage::class, FeatureEventMessage.serializer())
            subclass(AgentStartingEvent::class, AgentStartingEvent.serializer())
            subclass(AgentCompletedEvent::class, AgentCompletedEvent.serializer())
            subclass(AgentClosingEvent::class, AgentClosingEvent.serializer())
            subclass(AgentExecutionFailedEvent::class, AgentExecutionFailedEvent.serializer())
            subclass(GraphStrategyStartingEvent::class, GraphStrategyStartingEvent.serializer())
            subclass(FunctionalStrategyStartingEvent::class, FunctionalStrategyStartingEvent.serializer())
            subclass(StrategyCompletedEvent::class, StrategyCompletedEvent.serializer())
            subclass(NodeExecutionStartingEvent::class, NodeExecutionStartingEvent.serializer())
            subclass(NodeExecutionCompletedEvent::class, NodeExecutionCompletedEvent.serializer())
            subclass(NodeExecutionFailedEvent::class, NodeExecutionFailedEvent.serializer())
            subclass(SubgraphExecutionStartingEvent::class, SubgraphExecutionStartingEvent.serializer())
            subclass(SubgraphExecutionCompletedEvent::class, SubgraphExecutionCompletedEvent.serializer())
            subclass(SubgraphExecutionFailedEvent::class, SubgraphExecutionFailedEvent.serializer())
            subclass(ToolCallStartingEvent::class, ToolCallStartingEvent.serializer())
            subclass(ToolValidationFailedEvent::class, ToolValidationFailedEvent.serializer())
            subclass(ToolCallFailedEvent::class, ToolCallFailedEvent.serializer())
            subclass(ToolCallCompletedEvent::class, ToolCallCompletedEvent.serializer())
            subclass(LLMCallStartingEvent::class, LLMCallStartingEvent.serializer())
            subclass(LLMCallCompletedEvent::class, LLMCallCompletedEvent.serializer())
            subclass(LLMStreamingStartingEvent::class, LLMStreamingStartingEvent.serializer())
            subclass(LLMStreamingFrameReceivedEvent::class, LLMStreamingFrameReceivedEvent.serializer())
            subclass(LLMStreamingFailedEvent::class, LLMStreamingFailedEvent.serializer())
            subclass(LLMStreamingCompletedEvent::class, LLMStreamingCompletedEvent.serializer())
        }

        polymorphic(FeatureEvent::class) {
            subclass(FeatureEventMessage::class, FeatureEventMessage.serializer())
            subclass(AgentStartingEvent::class, AgentStartingEvent.serializer())
            subclass(AgentCompletedEvent::class, AgentCompletedEvent.serializer())
            subclass(AgentClosingEvent::class, AgentClosingEvent.serializer())
            subclass(AgentExecutionFailedEvent::class, AgentExecutionFailedEvent.serializer())
            subclass(GraphStrategyStartingEvent::class, GraphStrategyStartingEvent.serializer())
            subclass(FunctionalStrategyStartingEvent::class, FunctionalStrategyStartingEvent.serializer())
            subclass(StrategyCompletedEvent::class, StrategyCompletedEvent.serializer())
            subclass(NodeExecutionStartingEvent::class, NodeExecutionStartingEvent.serializer())
            subclass(NodeExecutionCompletedEvent::class, NodeExecutionCompletedEvent.serializer())
            subclass(NodeExecutionFailedEvent::class, NodeExecutionFailedEvent.serializer())
            subclass(SubgraphExecutionStartingEvent::class, SubgraphExecutionStartingEvent.serializer())
            subclass(SubgraphExecutionCompletedEvent::class, SubgraphExecutionCompletedEvent.serializer())
            subclass(SubgraphExecutionFailedEvent::class, SubgraphExecutionFailedEvent.serializer())
            subclass(ToolCallStartingEvent::class, ToolCallStartingEvent.serializer())
            subclass(ToolValidationFailedEvent::class, ToolValidationFailedEvent.serializer())
            subclass(ToolCallFailedEvent::class, ToolCallFailedEvent.serializer())
            subclass(ToolCallCompletedEvent::class, ToolCallCompletedEvent.serializer())
            subclass(LLMCallStartingEvent::class, LLMCallStartingEvent.serializer())
            subclass(LLMCallCompletedEvent::class, LLMCallCompletedEvent.serializer())
            subclass(LLMStreamingStartingEvent::class, LLMStreamingStartingEvent.serializer())
            subclass(LLMStreamingFrameReceivedEvent::class, LLMStreamingFrameReceivedEvent.serializer())
            subclass(LLMStreamingFailedEvent::class, LLMStreamingFailedEvent.serializer())
            subclass(LLMStreamingCompletedEvent::class, LLMStreamingCompletedEvent.serializer())
        }

        polymorphic(DefinedFeatureEvent::class) {
            subclass(AgentStartingEvent::class, AgentStartingEvent.serializer())
            subclass(AgentCompletedEvent::class, AgentCompletedEvent.serializer())
            subclass(AgentClosingEvent::class, AgentClosingEvent.serializer())
            subclass(AgentExecutionFailedEvent::class, AgentExecutionFailedEvent.serializer())
            subclass(GraphStrategyStartingEvent::class, GraphStrategyStartingEvent.serializer())
            subclass(FunctionalStrategyStartingEvent::class, FunctionalStrategyStartingEvent.serializer())
            subclass(StrategyCompletedEvent::class, StrategyCompletedEvent.serializer())
            subclass(NodeExecutionStartingEvent::class, NodeExecutionStartingEvent.serializer())
            subclass(NodeExecutionCompletedEvent::class, NodeExecutionCompletedEvent.serializer())
            subclass(NodeExecutionFailedEvent::class, NodeExecutionFailedEvent.serializer())
            subclass(SubgraphExecutionStartingEvent::class, SubgraphExecutionStartingEvent.serializer())
            subclass(SubgraphExecutionCompletedEvent::class, SubgraphExecutionCompletedEvent.serializer())
            subclass(SubgraphExecutionFailedEvent::class, SubgraphExecutionFailedEvent.serializer())
            subclass(ToolCallStartingEvent::class, ToolCallStartingEvent.serializer())
            subclass(ToolValidationFailedEvent::class, ToolValidationFailedEvent.serializer())
            subclass(ToolCallFailedEvent::class, ToolCallFailedEvent.serializer())
            subclass(ToolCallCompletedEvent::class, ToolCallCompletedEvent.serializer())
            subclass(LLMCallStartingEvent::class, LLMCallStartingEvent.serializer())
            subclass(LLMCallCompletedEvent::class, LLMCallCompletedEvent.serializer())
            subclass(LLMStreamingStartingEvent::class, LLMStreamingStartingEvent.serializer())
            subclass(LLMStreamingFrameReceivedEvent::class, LLMStreamingFrameReceivedEvent.serializer())
            subclass(LLMStreamingFailedEvent::class, LLMStreamingFailedEvent.serializer())
            subclass(LLMStreamingCompletedEvent::class, LLMStreamingCompletedEvent.serializer())
        }

        polymorphic(StrategyStartingEvent::class) {
            subclass(GraphStrategyStartingEvent::class, GraphStrategyStartingEvent.serializer())
            subclass(FunctionalStrategyStartingEvent::class, FunctionalStrategyStartingEvent.serializer())
        }
    }

internal fun featureMessageJsonConfig(serializersModule: SerializersModule? = null): Json {
    return serializersModule?.let { modules ->
        Json(defaultFeatureMessageJsonConfig) {
            this.serializersModule += modules
        }
    } ?: defaultFeatureMessageJsonConfig
}

@InternalAPI
@Suppress("unused")
internal class FeatureMessagesSerializerCollector : SerializersModuleCollector {
    private val serializers = mutableListOf<String>()

    override fun <T : Any> contextual(
        kClass: KClass<T>,
        provider: (List<KSerializer<*>>) -> KSerializer<*>
    ) {
        serializers += "[Contextual] class: ${kClass.simpleName}"
    }

    override fun <Base : Any, Sub : Base> polymorphic(
        baseClass: KClass<Base>,
        actualClass: KClass<Sub>,
        actualSerializer: KSerializer<Sub>
    ) {
        serializers += "[Polymorphic] baseClass: ${baseClass.simpleName}, actualClass: ${actualClass.simpleName}"
    }

    override fun <Base : Any> polymorphicDefaultSerializer(
        baseClass: KClass<Base>,
        defaultSerializerProvider: (Base) -> SerializationStrategy<Base>?
    ) {
        serializers += "[Polymorphic Default] baseClass: ${baseClass.simpleName}"
    }

    override fun <Base : Any> polymorphicDefaultDeserializer(
        baseClass: KClass<Base>,
        defaultDeserializerProvider: (String?) -> DeserializationStrategy<Base>?
    ) {
        serializers += "[Polymorphic] baseClass: ${baseClass.simpleName}"
    }

    override fun toString(): String {
        return serializers.joinToString("\n") { " * $it" }
    }
}
