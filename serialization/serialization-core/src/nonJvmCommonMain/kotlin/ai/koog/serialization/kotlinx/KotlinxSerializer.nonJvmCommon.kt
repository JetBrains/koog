package ai.koog.serialization.kotlinx

import ai.koog.serialization.KSerializerTypeToken
import ai.koog.serialization.KotlinClassToken
import ai.koog.serialization.KotlinTypeToken
import ai.koog.serialization.TypeToken
import ai.koog.serialization.annotations.InternalKoogSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@OptIn(InternalKoogSerializationApi::class)
internal actual fun <T> resolveSerializer(typeToken: TypeToken, json: Json): KSerializer<T> {
    val serializersModule = json.serializersModule

    val serializer = when (typeToken) {
        is KotlinTypeToken ->
            serializersModule.serializer(typeToken.type)

        is KotlinClassToken ->
            serializersModule.serializer(
                kClass = typeToken.klass,
                typeArgumentsSerializers = typeToken.typeArguments.map { resolveSerializer<Any?>(it, json) },
                isNullable = false,
            )

        is KSerializerTypeToken<*> ->
            typeToken.serializer
    }

    @Suppress("UNCHECKED_CAST")
    return serializer as KSerializer<T>
}
