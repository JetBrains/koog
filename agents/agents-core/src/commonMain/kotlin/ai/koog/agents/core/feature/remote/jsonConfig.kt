package ai.koog.agents.core.feature.remote

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.FeatureEventMessage
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.model.events.AIAgentBeforeCloseEvent
import ai.koog.agents.core.feature.model.events.AIAgentFinishedEvent
import ai.koog.agents.core.feature.model.events.AIAgentFunctionalStrategyStartEvent
import ai.koog.agents.core.feature.model.events.AIAgentGraphStrategyStartEvent
import ai.koog.agents.core.feature.model.events.AIAgentNodeExecutionEndEvent
import ai.koog.agents.core.feature.model.events.AIAgentNodeExecutionErrorEvent
import ai.koog.agents.core.feature.model.events.AIAgentNodeExecutionStartEvent
import ai.koog.agents.core.feature.model.events.AIAgentRunErrorEvent
import ai.koog.agents.core.feature.model.events.AIAgentStartedEvent
import ai.koog.agents.core.feature.model.events.AIAgentStrategyFinishedEvent
import ai.koog.agents.core.feature.model.events.AIAgentStrategyStartEvent
import ai.koog.agents.core.feature.model.events.AfterLLMCallEvent
import ai.koog.agents.core.feature.model.events.BeforeLLMCallEvent
import ai.koog.agents.core.feature.model.events.DefinedFeatureEvent
import ai.koog.agents.core.feature.model.events.ToolCallEvent
import ai.koog.agents.core.feature.model.events.ToolCallFailureEvent
import ai.koog.agents.core.feature.model.events.ToolCallResultEvent
import ai.koog.agents.core.feature.model.events.ToolValidationErrorEvent
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
 * - [AIAgentStartedEvent] - Fired when an AI agent starts execution
 * - [AIAgentFinishedEvent] - Fired when an AI agent completes execution
 * - [AIAgentBeforeCloseEvent] - Fired before an AI agent is closed
 * - [AIAgentRunErrorEvent] - Fired when an AI agent encounters a runtime error
 * - [AIAgentStrategyStartEvent] - Fired when an AI agent strategy begins
 * - [AIAgentStrategyFinishedEvent] - Fired when an AI agent strategy completes
 * - [AIAgentNodeExecutionStartEvent] - Fired when a node execution starts
 * - [AIAgentNodeExecutionEndEvent] - Fired when a node execution ends
 * - [ToolCallEvent] - Fired when a tool is called
 * - [ToolValidationErrorEvent] - Fired when tool validation fails
 * - [ToolCallFailureEvent] - Fired when a tool call fails
 * - [ToolCallResultEvent] - Fired when a tool call returns a result
 * - [BeforeLLMCallEvent] - Fired before making an LLM call
 * - [AfterLLMCallEvent] - Fired after completing an LLM call
 *
 * This configuration enables proper handling of the diverse event types encountered in the system by ensuring
 * that the polymorphic serialization framework can correctly serialize and deserialize each subclass.
 */
public val defaultFeatureMessageSerializersModule: SerializersModule
    get() = SerializersModule {

        polymorphic(FeatureMessage::class) {
            subclass(FeatureStringMessage::class, FeatureStringMessage.serializer())
            subclass(FeatureEventMessage::class, FeatureEventMessage.serializer())
            subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
            subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
            subclass(AIAgentBeforeCloseEvent::class, AIAgentBeforeCloseEvent.serializer())
            subclass(AIAgentRunErrorEvent::class, AIAgentRunErrorEvent.serializer())
            subclass(AIAgentGraphStrategyStartEvent::class, AIAgentGraphStrategyStartEvent.serializer())
            subclass(AIAgentFunctionalStrategyStartEvent::class, AIAgentFunctionalStrategyStartEvent.serializer())
            subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
            subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
            subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
            subclass(AIAgentNodeExecutionErrorEvent::class, AIAgentNodeExecutionErrorEvent.serializer())
            subclass(ToolCallEvent::class, ToolCallEvent.serializer())
            subclass(ToolValidationErrorEvent::class, ToolValidationErrorEvent.serializer())
            subclass(ToolCallFailureEvent::class, ToolCallFailureEvent.serializer())
            subclass(ToolCallResultEvent::class, ToolCallResultEvent.serializer())
            subclass(BeforeLLMCallEvent::class, BeforeLLMCallEvent.serializer())
            subclass(AfterLLMCallEvent::class, AfterLLMCallEvent.serializer())
        }

        polymorphic(FeatureEvent::class) {
            subclass(FeatureEventMessage::class, FeatureEventMessage.serializer())
            subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
            subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
            subclass(AIAgentBeforeCloseEvent::class, AIAgentBeforeCloseEvent.serializer())
            subclass(AIAgentRunErrorEvent::class, AIAgentRunErrorEvent.serializer())
            subclass(AIAgentGraphStrategyStartEvent::class, AIAgentGraphStrategyStartEvent.serializer())
            subclass(AIAgentFunctionalStrategyStartEvent::class, AIAgentFunctionalStrategyStartEvent.serializer())
            subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
            subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
            subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
            subclass(AIAgentNodeExecutionErrorEvent::class, AIAgentNodeExecutionErrorEvent.serializer())
            subclass(ToolCallEvent::class, ToolCallEvent.serializer())
            subclass(ToolValidationErrorEvent::class, ToolValidationErrorEvent.serializer())
            subclass(ToolCallFailureEvent::class, ToolCallFailureEvent.serializer())
            subclass(ToolCallResultEvent::class, ToolCallResultEvent.serializer())
            subclass(BeforeLLMCallEvent::class, BeforeLLMCallEvent.serializer())
            subclass(AfterLLMCallEvent::class, AfterLLMCallEvent.serializer())
        }

        polymorphic(DefinedFeatureEvent::class) {
            subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
            subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
            subclass(AIAgentBeforeCloseEvent::class, AIAgentBeforeCloseEvent.serializer())
            subclass(AIAgentRunErrorEvent::class, AIAgentRunErrorEvent.serializer())
            subclass(AIAgentGraphStrategyStartEvent::class, AIAgentGraphStrategyStartEvent.serializer())
            subclass(AIAgentFunctionalStrategyStartEvent::class, AIAgentFunctionalStrategyStartEvent.serializer())
            subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
            subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
            subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
            subclass(AIAgentNodeExecutionErrorEvent::class, AIAgentNodeExecutionErrorEvent.serializer())
            subclass(ToolCallEvent::class, ToolCallEvent.serializer())
            subclass(ToolValidationErrorEvent::class, ToolValidationErrorEvent.serializer())
            subclass(ToolCallFailureEvent::class, ToolCallFailureEvent.serializer())
            subclass(ToolCallResultEvent::class, ToolCallResultEvent.serializer())
            subclass(BeforeLLMCallEvent::class, BeforeLLMCallEvent.serializer())
            subclass(AfterLLMCallEvent::class, AfterLLMCallEvent.serializer())
        }

        polymorphic(AIAgentStrategyStartEvent::class) {
            subclass(AIAgentGraphStrategyStartEvent::class, AIAgentGraphStrategyStartEvent.serializer())
            subclass(AIAgentFunctionalStrategyStartEvent::class, AIAgentFunctionalStrategyStartEvent.serializer())
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
