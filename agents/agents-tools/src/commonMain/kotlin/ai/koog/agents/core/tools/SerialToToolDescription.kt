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
 * Converts a [SerialDescriptor] into a [ToolDescriptor] with metadata about a tool,
 * including its name, description, and parameters.
 *
 * @param toolName The name to assign to the resulting tool descriptor.
 * @param toolDescription An optional custom description for the tool. Defaults to the descriptor's annotation-based description if null.
 * @return A [ToolDescriptor] representing the tool's schema, including its name, description, and any parameters.
 *
 *
 *
 * **Example:** if the current [SerialDescriptor] represents the following class:
 * ```kotlin
 * @Serializable
 * class Person(
 *      val name: String,
 *      @property:LLMDescription("Age of the user (between 5 and 99)")
 *      val age: Int
 * )
 * ```
 * ,then
 * ```kotlin
 * serializer<Person>().descriptor
 *     .asToolDescriptor(
 *         toolName = "getLocation",
 *         toolDescription = "Finds where the given Person is located"
 *     )
 * ```
 * would return the following `ToolDescriptor` :
 * ```kotlin
 * ToolDescriptor(
 *     name = "getLocation",
 *     description = "Finds where the given Person is located",
 *     requiredParameters = listOf(
 *         ToolParameterDescriptor(
 *             name = "name",
 *             description = "name",
 *             type = ToolParameterType.String
 *         ),
 *         ToolParameterDescriptor(
 *             name = "age",
 *             description = "Age of the user (between 5 and 99)",
 *             type = ToolParameterType.Integer
 *         )
 *     )
 * )
 * ```
 *
 * Or, alternatively, you can ommit the `toolDescription` parameter but provide it via `@LLMDescription` annotation of your class:
 *
 * ```kotlin
 * @Serializable
 * @LLMDescription("A tool to compile the final plan of the trip accepted by the user")
 * class TripPlan(
 *     @property:LLMDescription("Steps of the plan, containing destination, start date and end date of each jorney")
 *     val steps: List<PlanStep>,
 * )
 * ```
 * ,then
 * ```kotlin
 * serializer<TripPlan>().descriptor
 *     .asToolDescriptor(toolName = "provideTripPlan")
 * ```
 * would return the following `ToolDescriptor` :
 * ```kotlin
 * ToolDescriptor(
 *     name = "provideTripPlan",
 *     description = "A tool to compile the final plan of the trip accepted by the user",
 *     requiredParameters = listOf(
 *         ToolParameterDescriptor(
 *             name = "steps",
 *             description = "Steps of the plan, containing destination, start date and end date of each jorney",
 *             type = ToolParameterType.List(itemType = ToolParameterType.Object(
 *                ... // fields of `PlanStep`
 *             ))
 *         )
 *     )
 * )
 * ```
 */
@InternalAgentToolsApi
public fun SerialDescriptor.asToolDescriptor(toolName: String, toolDescription: String? = null): ToolDescriptor {
    val description = toolDescription ?: description()

    return when (kind) {
        PrimitiveKind.STRING -> ToolParameterType.String.asValueTool(toolName, description)
        PrimitiveKind.BOOLEAN -> ToolParameterType.Boolean.asValueTool(toolName, description)
        PrimitiveKind.CHAR -> ToolParameterType.String.asValueTool(toolName, description)
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        PrimitiveKind.LONG -> ToolParameterType.Integer.asValueTool(toolName, description)

        PrimitiveKind.FLOAT,
        PrimitiveKind.DOUBLE -> ToolParameterType.Float.asValueTool(toolName, description)

        StructureKind.LIST -> ToolParameterType.List(
            getElementDescriptor(0).toToolParameterType()
        ).asValueTool(toolName, description)

        SerialKind.ENUM -> ToolParameterType.Enum(Array(elementsCount, ::getElementName))
            .asValueTool(toolName, description)

        StructureKind.CLASS -> {
            val required = mutableListOf<String>()
            val properties = parameterDescriptors(required)
            ToolDescriptor(
                toolName,
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
            name = toolName,
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
        val isOptional = isElementOptional(i) || descriptor.isNullable

        if (!isOptional) {
            required.add(name)
        }

        ToolParameterDescriptor(
            name,
            getElementAnnotations(i).filterIsInstance<LLMDescription>().firstOrNull()?.description ?: "",
            getElementDescriptor(i).toToolParameterType()
        )
    }
