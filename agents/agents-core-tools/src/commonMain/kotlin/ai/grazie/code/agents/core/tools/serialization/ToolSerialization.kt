package ai.grazie.code.agents.core.tools.serialization

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

internal val JsonForTool = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
internal data class ToolModel(
    val name: String,
    val description: String,
    @SerialName("required_parameters")
    val requiredParameters: List<ToolParameterModel>,
    @SerialName("optional_parameters")
    val optionalParameters: List<ToolParameterModel>,
)

@Serializable
internal data class ToolParameterModel(
    val name: String,
    val type: String,
    val description: String,
    // for enums
    @SerialName("enum") val enumValues: List<String>? = null,
    // for lists
    @SerialName("items") val itemType: ToolArrayItemTypeModel? = null,
    /*
      The exact type depends on the particular parameter type, so it's easier to make it "dynamic"
      instead of dealing with custom serializers.
    */
    val default: JsonElement? = null,
)

@Serializable
internal data class ToolArrayItemTypeModel(
    val type: String,
    val enum: List<String>? = null,
    val items: ToolArrayItemTypeModel? = null
)

/**
 * Serializes a list of ToolDescriptor objects into a JSON string representation.
 */
fun serializeToolDescriptorsToJsonString(toolDescriptors: List<ToolDescriptor>): String {
    val toolModels = toolDescriptors.map {
        ToolModel(
            name = it.name,
            description = it.description,
            requiredParameters = it.requiredParameters.map { it.toToolParameterModel() },
            optionalParameters = it.optionalParameters.map { it.toToolParameterModel() }
        )
    }

    return JsonForTool.encodeToString(toolModels)
}

private fun <T> ToolParameterDescriptor<T>.toToolParameterModel(): ToolParameterModel = ToolParameterModel(
    name = name,
    type = type.name,
    description = description,
    enumValues = (type as? ToolParameterType.Enum<*>)?.toToolEnumValues(),
    itemType = (type as? ToolParameterType.List<*>)?.toToolArrayItemType(),
    default = defaultValue?.let { JsonForTool.encodeToJsonElement(type.serializer, it) }
)


private fun <T : Enum<T>> ToolParameterType.Enum<T>.toToolEnumValues(): List<String> = entries.map {
    JsonForTool.encodeToJsonElement(serializer, it).jsonPrimitive.content
}

private fun ToolParameterType.List<*>.toToolArrayItemType(): ToolArrayItemTypeModel = ToolArrayItemTypeModel(
    type = itemsType.name,
    enum = (itemsType as? ToolParameterType.Enum<*>)?.toToolEnumValues(),
    items = (itemsType as? ToolParameterType.List<*>)?.toToolArrayItemType()
)

