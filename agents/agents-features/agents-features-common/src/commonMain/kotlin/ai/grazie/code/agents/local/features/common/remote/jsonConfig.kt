package ai.grazie.code.agents.local.features.common.remote

import ai.grazie.code.agents.local.features.common.model.*
import ai.grazie.code.agents.core.feature.message.FeatureEvent
import ai.grazie.code.agents.core.feature.message.FeatureEventMessage
import ai.grazie.code.agents.core.feature.message.FeatureMessage
import ai.grazie.code.agents.core.feature.message.FeatureStringMessage
import io.ktor.utils.io.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlin.reflect.KClass

private val defaultFeatureMessageJsonConfig: Json
    get() = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
        serializersModule = SerializersModule {

            polymorphic(FeatureMessage::class) {
                subclass(FeatureStringMessage::class, FeatureStringMessage.serializer())
                subclass(FeatureEventMessage::class, FeatureEventMessage.serializer())

                subclass(AgentCreateEvent::class, AgentCreateEvent.serializer())
                subclass(StrategyStartEvent::class, StrategyStartEvent.serializer())
                subclass(NodeExecutionStartEvent::class, NodeExecutionStartEvent.serializer())
                subclass(NodeExecutionEndEvent::class, NodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
                subclass(ToolCallsStartEvent::class, ToolCallsStartEvent.serializer())
                subclass(ToolCallsEndEvent::class, ToolCallsEndEvent.serializer())
            }

            polymorphic(FeatureEvent::class) {
                subclass(FeatureEventMessage::class, FeatureEventMessage.serializer())

                subclass(AgentCreateEvent::class, AgentCreateEvent.serializer())
                subclass(StrategyStartEvent::class, StrategyStartEvent.serializer())
                subclass(NodeExecutionStartEvent::class, NodeExecutionStartEvent.serializer())
                subclass(NodeExecutionEndEvent::class, NodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
                subclass(ToolCallsStartEvent::class, ToolCallsStartEvent.serializer())
                subclass(ToolCallsEndEvent::class, ToolCallsEndEvent.serializer())
            }

            polymorphic(DefinedFeatureEvent::class) {
                subclass(AgentCreateEvent::class, AgentCreateEvent.serializer())
                subclass(StrategyStartEvent::class, StrategyStartEvent.serializer())
                subclass(NodeExecutionStartEvent::class, NodeExecutionStartEvent.serializer())
                subclass(NodeExecutionEndEvent::class, NodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
                subclass(ToolCallsStartEvent::class, ToolCallsStartEvent.serializer())
                subclass(ToolCallsEndEvent::class, ToolCallsEndEvent.serializer())
            }
        }
    }

fun featureMessageJsonConfig(serializersModule: SerializersModule? = null): Json {
    return serializersModule?.let { modules ->
        Json(defaultFeatureMessageJsonConfig) {
            this.serializersModule += modules
        }
    } ?: defaultFeatureMessageJsonConfig
}

@InternalAPI
@Suppress("unused")
class FeatureMessagesSerializerCollector : SerializersModuleCollector {
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