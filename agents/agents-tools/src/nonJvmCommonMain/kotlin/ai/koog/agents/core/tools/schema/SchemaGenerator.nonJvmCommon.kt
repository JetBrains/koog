package ai.koog.agents.core.tools.schema

import ai.koog.serialization.KSerializerTypeToken
import ai.koog.serialization.KotlinClassToken
import ai.koog.serialization.KotlinTypeToken
import ai.koog.serialization.TypeToken
import ai.koog.serialization.annotations.InternalKoogSerializationApi
import kotlinx.schema.generator.json.JsonSchemaConfig
import kotlinx.schema.json.JsonSchema
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull

@OptIn(InternalKoogSerializationApi::class)
internal actual fun getJsonSchema(
    typeToken: TypeToken,
    jsonSchemaConfig: JsonSchemaConfig,
): JsonSchema {
    val kSerializer = findKSerializer(typeToken)
        ?: throw IllegalArgumentException(
            "KSerializer for $typeToken not found. " +
                "On non-JVM platforms, automatic JSON schema generations is supported only for classes annotated with " +
                "@Serializable or if KSerializer with a proper SerialDescriptor is provided explicitly",
        )

    return createSerializationGenerator(jsonSchemaConfig)
        .generateSchema(kSerializer.descriptor)
}

@InternalKoogSerializationApi
private fun findKSerializer(typeToken: TypeToken): KSerializer<*>? {
    when (typeToken) {
        is KotlinTypeToken ->
            return serializerOrNull(typeToken.type)

        is KotlinClassToken ->
            return serializer(
                kClass = typeToken.klass,
                typeArgumentsSerializers =
                typeToken.typeArguments
                    .map { findKSerializer(it) ?: return null },
                isNullable = false,
            )

        is KSerializerTypeToken<*> ->
            return typeToken.serializer
    }
}
