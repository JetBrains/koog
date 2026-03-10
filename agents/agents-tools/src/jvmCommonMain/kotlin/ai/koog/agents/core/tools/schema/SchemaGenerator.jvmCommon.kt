package ai.koog.agents.core.tools.schema

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.serialization.JavaClassToken
import ai.koog.serialization.JavaTypeToken
import ai.koog.serialization.KSerializerTypeToken
import ai.koog.serialization.KotlinClassToken
import ai.koog.serialization.KotlinTypeToken
import ai.koog.serialization.TypeToken
import ai.koog.serialization.annotations.InternalKoogSerializationApi
import kotlinx.schema.generator.json.FunctionCallingSchemaConfig
import kotlinx.schema.generator.json.JsonSchemaConfig
import kotlinx.schema.generator.json.ReflectionClassJsonSchemaGenerator
import kotlinx.schema.generator.json.ReflectionFunctionCallingSchemaGenerator
import kotlinx.schema.json.JsonSchema
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KCallable
import kotlin.reflect.KClass

private fun createReflectionClassGenerator(
    jsonSchemaConfig: JsonSchemaConfig,
) = ReflectionClassJsonSchemaGenerator(
    // FIXME don't require Json
    json = Json.Default,
    config = jsonSchemaConfig,
)

private fun createReflectionFunctionGenerator(
    jsonSchemaConfig: JsonSchemaConfig,
) = ReflectionFunctionCallingSchemaGenerator(
    // FIXME don't require Json
    json = Json.Default,
    // FIXME provide convenience constructor FunctionCallingSchemaConfig(jsonSchemaConfig)
    config = FunctionCallingSchemaConfig(
        respectDefaultPresence = jsonSchemaConfig.respectDefaultPresence,
        requireNullableFields = jsonSchemaConfig.requireNullableFields,
        useUnionTypes = jsonSchemaConfig.useUnionTypes,
        useNullableField = jsonSchemaConfig.useNullableField,
        includePolymorphicDiscriminator = jsonSchemaConfig.includePolymorphicDiscriminator,
    ),
)

@OptIn(InternalKoogSerializationApi::class)
internal actual fun getJsonSchema(
    typeToken: TypeToken,
    jsonSchemaConfig: JsonSchemaConfig,
): JsonSchema {
    // Try to use KSerializer first
    val kSerializer = findKSerializer(typeToken)

    return if (kSerializer != null) {
        createSerializationGenerator(jsonSchemaConfig)
            .generateSchema(kSerializer.descriptor)
    } else {
        // Fallback to reflection
        val klass = when (typeToken) {
            is KotlinTypeToken -> {
                typeToken.type.classifier as? KClass<*>
                    ?: throw IllegalArgumentException(
                        "Can't generate JSON schema using reflection for ${typeToken.type} since it doesn't represent a Kotlin class"
                    )
            }

            is KotlinClassToken ->
                typeToken.klass

            is JavaTypeToken -> when (val type = typeToken.type) {
                is Class<*> -> type.kotlin

                is ParameterizedType -> {
                    val rawType = type.rawType as? Class<*>
                        ?: throw IllegalArgumentException("Unsupported Type.rawType, must be Class, got: ${type.rawType}")
                    rawType.kotlin
                }

                else -> throw IllegalArgumentException(
                    "Unsupported Java type for schema generation: ${typeToken.type}. Only classes are supported"
                )
            }

            is JavaClassToken ->
                typeToken.klass.kotlin

            is KSerializerTypeToken<*> ->
                throw IllegalStateException("Should not be possible, KSerializer is always present for this type token")
        }

        createReflectionClassGenerator(jsonSchemaConfig)
            .generateSchema(klass)
    }
}

/**
 * Generates a [ToolDescriptor] by generating and converting the function calling schema for the provided [callable]
 *
 * @param callable The callable to generate the schema for.
 * @param toolName Optional custom name. If not provided, [KCallable.name] will be used.
 * @param toolDescription Optional custom description.
 * If not provided, [ai.koog.agents.core.tools.annotations.LLMDescription.description] will be used if the [callable] is annotated with it.
 * @param jsonSchemaConfig Optional custom [JsonSchemaConfig] for the JSON schema generation.
 */
@InternalAgentToolsApi
public fun getToolDescriptor(
    callable: KCallable<*>,
    toolName: String? = null,
    toolDescription: String? = null,
    jsonSchemaConfig: JsonSchemaConfig = defaultJsonSchemaConfig,
): ToolDescriptor {
    val schema = createReflectionFunctionGenerator(jsonSchemaConfig)
        .generateSchema(callable)

    // All parameters for function calling are considered required
    val requiredParameters = schema.parameters.properties
        .orEmpty()
        .map { (name, property) ->
            val parameterInfo = property.toToolParameter(defs = null) // no defs in function calling schema

            ToolParameterDescriptor(
                name = name,
                description = parameterInfo.description,
                type = parameterInfo.type
            )
        }

    return ToolDescriptor(
        name = toolName ?: schema.name,
        description = toolDescription ?: schema.description.orEmpty(),
        requiredParameters = requiredParameters,
    )
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

        is JavaTypeToken ->
            return serializerOrNull(typeToken.type)

        is JavaClassToken ->
            return serializer(
                kClass = typeToken.klass.kotlin,
                typeArgumentsSerializers = typeToken.typeArguments
                    .map { findKSerializer(it) ?: return null },
                isNullable = false,
            )
    }
}
