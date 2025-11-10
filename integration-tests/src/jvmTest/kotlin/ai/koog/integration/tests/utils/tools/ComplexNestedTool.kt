package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

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

/**
 * A complex nested tool that demonstrates the JSON schema validation error.
 * This tool has parameters with complex nested structures that would trigger
 * the error in the Anthropic API before the fix.
 */
object ComplexNestedTool : SimpleTool<ComplexNestedToolArgs>() {
    override val argsSerializer = ComplexNestedToolArgs.serializer()

    override val name = "complex_nested_tool"

    override val description = "A tool that processes user profiles with complex nested structures."

    override suspend fun doExecute(args: ComplexNestedToolArgs): String {
        // Process the user profile
        val profile = args.profile
        val addressesInfo = profile.addresses.joinToString("\n") { address ->
            "- ${address.type} Address: ${address.street}, ${address.city}, ${address.state} ${address.zipCode}"
        }

        return """
                Successfully processed user profile:
                Name: ${profile.name}
                Email: ${profile.email}
                Addresses:
                $addressesInfo
        """.trimIndent()
    }
}
