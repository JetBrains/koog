package ai.koog.agents.core.feature.remote

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureEventMessage
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureStringMessage
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

private val defaultFeatureMessageJsonConfig: Json
    get() = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
        serializersModule = defaultFeatureMessageSerializersModule
    }

private val defaultFeatureMessageSerializersModule: SerializersModule
    get() = SerializersModule {
        polymorphic(FeatureMessage::class) {
            subclass(FeatureStringMessage::class, FeatureStringMessage.serializer())
            subclass(FeatureEventMessage::class, FeatureEventMessage.serializer())
        }

        polymorphic(FeatureEvent::class) {
            subclass(FeatureEventMessage::class, FeatureEventMessage.serializer())
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
