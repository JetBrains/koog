package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

private fun SerialDescriptor.description(): String =
    annotations.filterIsInstance<LLMDescription>().firstOrNull()?.description ?: ""

/**
 * Convert a [SerialDescriptor] to a [ToolDescriptor].
 */
@InternalAgentToolsApi
public fun SerialDescriptor.toolDescription(name: String): ToolDescriptor {
    val description = description()

    return when (kind) {
        PrimitiveKind.STRING -> ToolParameterType.String.asValueTool(name, description)
        PrimitiveKind.BOOLEAN -> ToolParameterType.Boolean.asValueTool(name, description)
        PrimitiveKind.CHAR -> ToolParameterType.String.asValueTool(name, description)
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        PrimitiveKind.LONG -> ToolParameterType.Integer.asValueTool(name, description)

        PrimitiveKind.FLOAT,
        PrimitiveKind.DOUBLE -> ToolParameterType.Float.asValueTool(name, description)

        StructureKind.LIST -> ToolParameterType.List(
            getElementDescriptor(0).toToolParameterType()
        ).asValueTool(name, description)

        SerialKind.ENUM -> ToolParameterType.Enum(Array(elementsCount, ::getElementName))
            .asValueTool(name, description)

        StructureKind.CLASS -> {
            val required = mutableListOf<String>()
            val properties = parameterDescriptors(required)
            ToolDescriptor(
                name,
                description,
                requiredParameters = properties.filter { required.contains(it.name) },
                optionalParameters = properties.filterNot { required.contains(it.name) }
            )
        }

        // support FreeForm Object ToolDescriptor
        PolymorphicKind.SEALED,
        StructureKind.OBJECT,
        SerialKind.CONTEXTUAL,
        PolymorphicKind.OPEN,
        StructureKind.MAP -> ToolDescriptor(
            name = name,
            description = description,
            requiredParameters = emptyList(),
            optionalParameters = emptyList()
        )
    }
}

private fun SerialDescriptor.toToolParameterType(): ToolParameterType = when (kind) {
    PrimitiveKind.CHAR,
    PrimitiveKind.STRING -> ToolParameterType.String

    PrimitiveKind.BOOLEAN -> ToolParameterType.Boolean
    PrimitiveKind.BYTE,
    PrimitiveKind.SHORT,
    PrimitiveKind.INT,
    PrimitiveKind.LONG -> ToolParameterType.Integer

    PrimitiveKind.FLOAT,
    PrimitiveKind.DOUBLE -> ToolParameterType.Float

    StructureKind.LIST -> ToolParameterType.List(getElementDescriptor(0).toToolParameterType())

    SerialKind.ENUM -> ToolParameterType.Enum(Array(elementsCount, ::getElementName))

    StructureKind.CLASS -> {
        val required = mutableListOf<String>()
        ToolParameterType.Object(
            parameterDescriptors(required),
            required,
            false
        )
    }

    PolymorphicKind.SEALED,
    StructureKind.OBJECT,
    SerialKind.CONTEXTUAL,
    PolymorphicKind.OPEN,
    StructureKind.MAP -> ToolParameterType.Object(
        emptyList(),
        emptyList(),
        true,
        ToolParameterType.String

    )
}

private fun ToolParameterType.asValueTool(name: String, description: String) = ToolDescriptor(
    name = name,
    description = description,
    requiredParameters = listOf(ToolParameterDescriptor(name = "value", description = "", this))
)

private fun SerialDescriptor.parameterDescriptors(required: MutableList<String>): List<ToolParameterDescriptor> =
    List(elementsCount) { i ->
        val name = getElementName(i)
        val descriptor = getElementDescriptor(i)
        if (!isElementOptional(i) || !descriptor.isNullable) required.add(name)
        ToolParameterDescriptor(
            name,
            annotations.filterIsInstance<LLMDescription>().firstOrNull()?.description ?: "",
            getElementDescriptor(i).toToolParameterType()
        )
    }
