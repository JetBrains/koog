package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val ToolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    decodeEnumsCaseInsensitive = true
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
)

@Serializable
internal data class ToolArrayItemTypeModel(
    val type: String,
    val enum: List<String>? = null,
    val items: ToolArrayItemTypeModel? = null
)

/**
 * Serializes a list of ToolDescriptor objects into a JSON string representation.
 *
 * @param toolDescriptors List of [ToolDescriptor]
 */
public fun serializeToolDescriptorsToJsonString(toolDescriptors: List<ToolDescriptor>): String {
    val toolModels = toolDescriptors.map {
        ToolModel(
            name = it.name,
            description = it.description,
            requiredParameters = it.requiredParameters.map { it.toToolParameterModel() },
            optionalParameters = it.optionalParameters.map { it.toToolParameterModel() }
        )
    }

    return ToolJson.encodeToString(toolModels)
}

private fun ToolParameterDescriptor.toToolParameterModel(): ToolParameterModel = ToolParameterModel(
    name = name,
    type = type.name,
    description = description,
    enumValues = (type as? ToolParameterType.Enum)?.toToolEnumValues(),
    itemType = (type as? ToolParameterType.List)?.toToolArrayItemType(),
)

private fun <T : Enum<T>> ToolParameterType.Enum.toToolEnumValues(): List<String> = entries.asList()

private fun ToolParameterType.List.toToolArrayItemType(): ToolArrayItemTypeModel = ToolArrayItemTypeModel(
    type = itemsType.name,
    enum = (itemsType as? ToolParameterType.Enum)?.toToolEnumValues(),
    items = (itemsType as? ToolParameterType.List)?.toToolArrayItemType()
)
