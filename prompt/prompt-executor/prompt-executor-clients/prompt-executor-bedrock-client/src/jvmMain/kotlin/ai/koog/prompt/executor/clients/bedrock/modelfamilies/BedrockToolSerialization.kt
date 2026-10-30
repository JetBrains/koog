package ai.koog.prompt.executor.clients.bedrock.modelfamilies

import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal object BedrockToolSerialization {

    /**
     * Builds a JSON schema for a tool parameter, including description.
     */
    internal fun buildToolParameterSchema(param: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", param.description)
        buildTypeSchema(param.type).forEach { (key, value) ->
            put(key, value)
        }
    }

    /**
     * Builds a JSON schema for a parameter type without description.
     * This helper function handles recursive type serialization cleanly.
     */
    @OptIn(InternalAgentToolsApi::class)
    private fun buildTypeSchema(type: ToolParameterType): JsonObject = buildJsonObject {
        when (type) {
            ToolParameterType.Boolean -> put("type", "boolean")
            ToolParameterType.Float -> put("type", "number")
            ToolParameterType.Integer -> put("type", "integer")
            ToolParameterType.String -> put("type", "string")
            ToolParameterType.Null -> put("type", "null")

            is ToolParameterType.Enum -> {
                put("type", "string")
                putJsonArray("enum") {
                    type.entries.forEach { entry ->
                        // Accept JSON-encoded strings as well as raw descriptor entries.
                        val value = if (entry.startsWith('"')) Json.decodeFromString<String>(entry) else entry
                        add(JsonPrimitive(value))
                    }
                }
            }

            is ToolParameterType.List -> {
                put("type", "array")
                put("items", buildTypeSchema(type.itemsType))
            }

            is ToolParameterType.AnyOf -> {
                // FIXME this is hack, represent union types properly in ToolDescriptor
                val nullableSchema = type.hackRepresentAnyOfWithNullAsTypeUnionWithNull(::buildTypeSchema)
                if (nullableSchema != null) {
                    nullableSchema.forEach { (key, value) -> put(key, value) }
                    // A type union alone does not make null a valid enum value.
                    (nullableSchema["enum"] as? JsonArray)?.let { values ->
                        put("enum", JsonArray(values + JsonNull))
                    }
                } else {
                    putJsonArray("anyOf") {
                        addAll(type.types.map { buildToolParameterSchema(it) })
                    }
                }
            }

            is ToolParameterType.Object -> {
                put("type", "object")
                putJsonObject("properties") {
                    type.properties.forEach { prop ->
                        put(prop.name, buildToolParameterSchema(prop))
                    }
                }
                if (type.requiredProperties.isNotEmpty()) {
                    putJsonArray("required") {
                        type.requiredProperties.forEach { add(JsonPrimitive(it)) }
                    }
                }
                type.additionalProperties?.let { allowed ->
                    val additionalType = type.additionalPropertiesType
                    if (allowed && additionalType != null) {
                        put("additionalProperties", buildTypeSchema(additionalType))
                    } else {
                        put("additionalProperties", allowed)
                    }
                }
            }
        }
    }
}
