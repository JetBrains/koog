package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Address type enum.
 */
@Serializable
enum class AddressType {
    HOME,
    WORK,
    OTHER
}

/**
 * An address with multiple fields.
 */
@Serializable
data class Address(
    @property:LLMDescription("The type of address (HOME, WORK, or OTHER)")
    val type: AddressType,
    @property:LLMDescription("The street address")
    val street: String,
    @property:LLMDescription("The city")
    val city: String,
    @property:LLMDescription("The state or province")
    val state: String,
    @property:LLMDescription("The ZIP or postal code")
    val zipCode: String
)

/**
 * A user profile with nested structures.
 */
@Serializable
data class UserProfile(
    @property:LLMDescription("The user's full name")
    val name: String,
    @property:LLMDescription("The user's email address")
    val email: String,
    @property:LLMDescription("The user's addresses")
    val addresses: List<Address>
)

/**
 * Arguments for the complex nested tool.
 */
@Serializable
data class ComplexNestedToolArgs(
    @property:LLMDescription("The user profile to process")
    val profile: UserProfile
)

object ComplexNestedTool : SimpleTool<ComplexNestedToolArgs>() {
    override val argsSerializer = ComplexNestedToolArgs.serializer()

    override val name = "complex_nested_tool"

    override val description = "A tool that processes user profiles with complex nested structures."

    override suspend fun doExecute(args: ComplexNestedToolArgs): String {
        return ""
    }
}

class ToolDescriptorGenerationTest {
    @Test
    fun tetComplexToolDescriptorGeneration() {
        val extectedDescriptor = ToolDescriptor(
            name = "complex_nested_tool",
            description = "A tool that processes user profiles with complex nested structures.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "profile",
                    description = "The user profile to process",
                    type = ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor(
                                name = "name",
                                description = "The user's full name",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "email",
                                description = "The user's email address",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "addresses",
                                description = "The user's addresses",
                                type = ToolParameterType.List(
                                    ToolParameterType.Object(
                                        properties = listOf(
                                            ToolParameterDescriptor(
                                                name = "type",
                                                description = "The type of address (HOME, WORK, or OTHER)",
                                                type = ToolParameterType.Enum(
                                                    AddressType.entries.map {
                                                        it.name
                                                    }.toTypedArray()
                                                )
                                            ),
                                            ToolParameterDescriptor(
                                                name = "street",
                                                description = "The street address",
                                                type = ToolParameterType.String
                                            ),
                                            ToolParameterDescriptor(
                                                name = "city",
                                                description = "The city",
                                                type = ToolParameterType.String
                                            ),
                                            ToolParameterDescriptor(
                                                name = "state",
                                                description = "The state or province",
                                                type = ToolParameterType.String
                                            ),
                                            ToolParameterDescriptor(
                                                name = "zipCode",
                                                description = "The ZIP or postal code",
                                                type = ToolParameterType.String
                                            )
                                        ),
                                        requiredProperties = listOf("type", "street", "city", "state", "zipCode"),
                                        additionalProperties = false
                                    )
                                )
                            )
                        ),
                        requiredProperties = listOf("name", "email", "addresses"),
                        additionalProperties = false
                    )
                )
            )
        )

        assertEquals(extectedDescriptor, ComplexNestedTool.descriptor)
    }
}
