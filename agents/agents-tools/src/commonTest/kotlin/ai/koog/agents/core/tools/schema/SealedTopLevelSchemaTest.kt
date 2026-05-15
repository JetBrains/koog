package ai.koog.agents.core.tools.schema

import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.serialization.typeToken
import kotlinx.schema.generator.json.JsonSchemaConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
class SealedTopLevelSchemaTest {

    @Serializable
    @SerialName("SealedOutput")
    sealed interface SealedOutput {
        @Serializable
        @SerialName("SealedOutputA")
        data class A(val payload: String) : SealedOutput

        @Serializable
        @SerialName("SealedOutputB")
        data class B(val value: Int) : SealedOutput
    }

    @Test
    fun testTopLevelSealedSchemaConvertsToAnyOfOfBranches() {
        val schema = getJsonSchema(
            typeToken<SealedOutput>(),
            JsonSchemaConfig(includePolymorphicDiscriminator = true),
        )
        val info = schema.toToolParameter(schema.defs)

        val anyOf = info.type as? ToolParameterType.AnyOf
        assertTrue(anyOf != null, "Expected AnyOf for top-level sealed type, got: ${info.type}")
        assertEquals(2, anyOf.types.size, "Expected one AnyOf branch per sealed subclass")

        val branchObjects = anyOf.types.map { it.type as? ToolParameterType.Object }
        assertTrue(branchObjects.all { it != null }, "Each AnyOf branch must be an Object")

        val branchPropertyNames = branchObjects.map { branch ->
            branch!!.properties.map { it.name }.toSet()
        }
        assertTrue(
            branchPropertyNames.any { "payload" in it },
            "A branch must include payload property"
        )
        assertTrue(
            branchPropertyNames.any { "value" in it },
            "B branch must include value property"
        )
    }

    @Test
    fun testTopLevelSealedSchemaIncludesDiscriminatorWhenRequested() {
        val schema = getJsonSchema(
            typeToken<SealedOutput>(),
            JsonSchemaConfig(includePolymorphicDiscriminator = true),
        )
        val info = schema.toToolParameter(schema.defs)
        val anyOf = info.type as ToolParameterType.AnyOf

        anyOf.types.forEach { branch ->
            val obj = branch.type as ToolParameterType.Object
            val discriminator = obj.properties.firstOrNull { it.name == "type" }
            assertTrue(
                discriminator != null,
                "Each branch must include a type discriminator property when " +
                    "includePolymorphicDiscriminator=true. Branch: $obj"
            )
            val enumType = discriminator.type as? ToolParameterType.Enum
            assertTrue(
                enumType != null && enumType.entries.size == 1,
                "Discriminator must be a single-value enum (const), got: ${discriminator.type}"
            )
        }
    }
}
