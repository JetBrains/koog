package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject

private fun SerialDescriptor.description(): String =
    annotations.filterIsInstance<LLMDescription>().firstOrNull()?.description ?: ""

/**
 * Converts a [SerialDescriptor] into a [ToolDescriptor] with metadata about a tool,
 * including its name, description, and parameters.
 *
 * @param toolName The name to assign to the resulting tool descriptor.
 * @param toolDescription An optional custom description for the tool. Defaults to the descriptor's annotation-based description if null.
 * @param valueDescription An optional description for value parameters of primitive types (not required for class-based
 * parameters with @LLMDescription annotation but recommended for String/Int/Float/List etc. tool parameters). If not
 * specified for a primitive input type, an empty description will be passed to LLM.
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
public fun SerialDescriptor.asToolDescriptor(
    toolName: String,
    toolDescription: String? = null,
    valueDescription: String? = null
): ToolDescriptor {
    val description = toolDescription ?: description()

    return when (kind) {
        PrimitiveKind.STRING -> ToolParameterType.String.asValueTool(toolName, description, valueDescription)
        PrimitiveKind.BOOLEAN -> ToolParameterType.Boolean.asValueTool(toolName, description, valueDescription)
        PrimitiveKind.CHAR -> ToolParameterType.String.asValueTool(toolName, description, valueDescription)
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        PrimitiveKind.LONG -> ToolParameterType.Integer.asValueTool(toolName, description, valueDescription)

        PrimitiveKind.FLOAT,
        PrimitiveKind.DOUBLE -> ToolParameterType.Float.asValueTool(toolName, description, valueDescription)

        StructureKind.LIST -> ToolParameterType.List(
            getElementDescriptor(0).toToolParameterType()
        ).asValueTool(toolName, description, valueDescription)

        SerialKind.ENUM -> ToolParameterType.Enum(Array(elementsCount, ::getElementName))
            .asValueTool(toolName, description, valueDescription)

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

/**
 * Provides a custom deserializer for tools using the `KSerializer` interface.
 * Converts the current serializer into a specialized tool descriptor deserializer
 * if the underlying descriptor has a primitive kind.
 *
 * Serializes and deserializes specific data structures while restricting usage of
 * unsupported operations, such as serialization.
 *
 * This function is a utility to adapt serializers for internal tooling.
 *
 * @return A `KSerializer` that acts as*/
@InternalAgentToolsApi
public fun <T> KSerializer<T>.asToolDescriptorDeserializer(json: Json = Json): KSerializer<T> {
    val kind = descriptor.kind

    return if (kind is PrimitiveKind) {
        object : KSerializer<T> {
            override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Primitive", kind)

            override fun serialize(encoder: Encoder, value: T) {
                throw UnsupportedOperationException("Serialization is not supported")
            }

            override fun deserialize(decoder: Decoder): T {
                val jsonDecoder = decoder as? JsonDecoder
                    ?: throw IllegalStateException("`asToolDescriptorDeserializer` for primitive types should be json decoder")

                return json.decodeFromJsonElement(
                    this@asToolDescriptorDeserializer,
                    jsonDecoder.decodeJsonElement().jsonObject.getValue("value")
                )
            }
        }
    } else {
        this
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

private fun ToolParameterType.asValueTool(name: String, description: String, valueDescription: String? = null) =
    ToolDescriptor(
        name = name,
        description = description,
        requiredParameters = listOf(ToolParameterDescriptor(name = "value", description = valueDescription ?: "", this))
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
            getElementAnnotations(i).filterIsInstance<LLMDescription>().firstOrNull()?.description ?: name,
            getElementDescriptor(i).toToolParameterType()
        )
    }
