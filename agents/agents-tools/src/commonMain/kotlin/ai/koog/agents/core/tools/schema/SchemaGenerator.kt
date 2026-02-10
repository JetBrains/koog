package ai.koog.agents.core.tools.schema

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.TypeToken
import kotlinx.schema.generator.json.JsonSchemaConfig
import kotlinx.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator
import kotlinx.schema.generator.json.serialization.SerializationClassSchemaIntrospector
import kotlinx.schema.json.AdditionalPropertiesSchema
import kotlinx.schema.json.AllowAdditionalProperties
import kotlinx.schema.json.AnyOfPropertyDefinition
import kotlinx.schema.json.ArrayPropertyDefinition
import kotlinx.schema.json.BooleanPropertyDefinition
import kotlinx.schema.json.CommonSchemaAttributes
import kotlinx.schema.json.DenyAdditionalProperties
import kotlinx.schema.json.JsonSchema
import kotlinx.schema.json.JsonSchemaConstants
import kotlinx.schema.json.NumericPropertyDefinition
import kotlinx.schema.json.ObjectPropertyDefinition
import kotlinx.schema.json.OneOfPropertyDefinition
import kotlinx.schema.json.PropertyDefinition
import kotlinx.schema.json.ReferencePropertyDefinition
import kotlinx.schema.json.StringPropertyDefinition
import kotlinx.schema.json.ValuePropertyDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun createSerializationGenerator(
    jsonSchemaConfig: JsonSchemaConfig,
) = SerializationClassJsonSchemaGenerator(
    introspectorConfig = SerializationClassSchemaIntrospector.Config(
        descriptionExtractor = { annotations ->
            annotations
                .filterIsInstance<LLMDescription>()
                .firstOrNull()?.description
        }
    ),
    json = Json.Default,
    jsonSchemaConfig = jsonSchemaConfig,
)

internal expect fun getJsonSchema(
    typeToken: TypeToken,
    jsonSchemaConfig: JsonSchemaConfig,
): JsonSchema

/**
 * Generates a [ToolDescriptor] by generating and converting the JSON schema for the type defined by the provided [argsType]
 *
 * @param argsType Type token representing arguments type.
 * @param toolName Name of the tool.
 * @param toolDescription Optional custom description. If not provided, the description will be obtained from the
 * generated JSON schema for the [argsType]
 * @param jsonSchemaConfig Optional custom [JsonSchemaConfig] for the JSON schema generation.
 */
@InternalAgentToolsApi
public fun getToolDescriptor(
    argsType: TypeToken,
    toolName: String,
    toolDescription: String? = null,
    jsonSchemaConfig: JsonSchemaConfig = JsonSchemaConfig.Default,
): ToolDescriptor {
    val schema = getJsonSchema(argsType, jsonSchemaConfig)

    if (JsonSchemaConstants.Types.OBJECT !in schema.type) {
        throw IllegalArgumentException("Only objects are supported as tool schemas, got ${schema.type}")
    }

    val (requiredParameters, optionalParameters) = schema.properties
        .map { (name, property) ->
            ToolParameterDescriptor(
                name = name,
                description = property.descriptionOrEmpty,
                type = property.toToolParameterType(schema.defs)
            )
        }
        .partition { it.name in schema.required }

    return ToolDescriptor(
        name = toolName,
        description = toolDescription ?: schema.description.orEmpty(),
        requiredParameters = requiredParameters,
        optionalParameters = optionalParameters,
    )
}

/**
 * Converts a JSON schema property representation [PropertyDefinition] to our tool parameter representation [ToolParameterType].
 * @param defs JSON schema definitions map for resolving references.
 */
internal fun PropertyDefinition.toToolParameterType(
    defs: Map<String, PropertyDefinition>?
): ToolParameterType = when (this) {
    is ValuePropertyDefinition<*> -> {
        val type = this.type
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Value property definition is missing the 'type' (either null or empty)")

        val isNullableType = JsonSchemaConstants.Types.NULL in type || nullable == true

        val parameterType = when (this) {
            is StringPropertyDefinition -> {
                val enum = this.enum
                val const = (this.constValue as? JsonPrimitive)?.contentOrNull

                when {
                    // Normal enum
                    enum != null -> ToolParameterType.Enum(enum.toTypedArray())

                    // Treat consts as enums with a single value. This is used with polymorphic discriminators
                    const != null -> ToolParameterType.Enum(arrayOf(const))

                    else -> ToolParameterType.String
                }
            }

            is BooleanPropertyDefinition ->
                ToolParameterType.Boolean

            is NumericPropertyDefinition -> when {
                JsonSchemaConstants.Types.INTEGER in type -> ToolParameterType.Integer
                JsonSchemaConstants.Types.NUMBER in type -> ToolParameterType.Float
                else -> throw IllegalArgumentException("Unsupported numeric type: $type")
            }

            is ArrayPropertyDefinition -> {
                ToolParameterType.List(
                    itemsType = items?.toToolParameterType(defs)
                        ?: throw IllegalArgumentException("Array property definition is missing the 'items' type")
                )
            }

            is ObjectPropertyDefinition -> {
                ToolParameterType.Object(
                    properties = properties
                        .orEmpty()
                        .map { (name, property) ->
                            ToolParameterDescriptor(
                                name = name,
                                description = (property as? CommonSchemaAttributes)?.description.orEmpty(),
                                type = property.toToolParameterType(defs)
                            )
                        },
                    requiredProperties = required.orEmpty(),
                    additionalProperties = when (additionalProperties) {
                        is AllowAdditionalProperties, is AdditionalPropertiesSchema -> true
                        is DenyAdditionalProperties, null -> false
                    },
                    additionalPropertiesType = (additionalProperties as? AdditionalPropertiesSchema)?.schema
                        ?.toToolParameterType(defs),
                )
            }

            else ->
                throw IllegalArgumentException("Unsupported value property definition type: $this")
        }

        if (isNullableType) {
            // emulate type union
            ToolParameterType.AnyOf(
                types = arrayOf(
                    ToolParameterDescriptor(type = ToolParameterType.Null, name = "", description = ""),
                    ToolParameterDescriptor(type = parameterType, name = "", description = ""),
                )
            )
        } else {
            parameterType
        }
    }

    is ReferencePropertyDefinition -> {
        val ref = this.ref
            ?: throw IllegalArgumentException("Reference property definition is missing the 'ref' attribute")
        val defs = defs
            ?: throw IllegalArgumentException("Encountered a ref in the JSON schema but the schema is missing the defs section")

        defs[ref.removePrefix(JsonSchemaConstants.Keys.REF_PREFIX)]
            ?.toToolParameterType(defs)
            ?: throw IllegalArgumentException("Can't find ref in defs: $ref. Schema defs: ${defs.keys}")
    }

    is AnyOfPropertyDefinition -> {
        ToolParameterType.AnyOf(
            types = anyOf
                .map {
                    ToolParameterDescriptor(
                        type = it.toToolParameterType(defs),
                        description = it.descriptionOrEmpty,
                        name = ""
                    )
                }
                .toTypedArray()
        )
    }

    // It isn't fully correct, but to keep the compatibility with ToolDescriptor for now consider oneOf == anyOf
    is OneOfPropertyDefinition -> {
        ToolParameterType.AnyOf(
            types = oneOf
                .map {
                    ToolParameterDescriptor(
                        type = it.toToolParameterType(defs),
                        description = it.descriptionOrEmpty,
                        name = ""
                    )
                }
                .toTypedArray()
        )
    }

    else ->
        throw IllegalArgumentException("Unsupported property definition type: $this")
}

internal val PropertyDefinition.descriptionOrEmpty: String
    get() = (this as? CommonSchemaAttributes)?.description.orEmpty()
