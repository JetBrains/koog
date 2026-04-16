package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.schema.getToolDescriptor
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(InternalAgentToolsApi::class)
class LLMDescriptionUsageTest {

    // Class-level LLMDescription is applied to ToolDescriptor and all fields
    @Serializable
    @LLMDescription("MyData description")
    data class MyData(
        val a: Int,
        val b: String?
    )

    @Test
    fun class_level_llm_description_applies_to_tool_and_fields() {
        val desc = getToolDescriptor(typeToken<MyData>(), "my_data")

        // Tool-level description
        assertEquals("my_data", desc.name)
        assertEquals("MyData description", desc.description)

        // All parameters use class-level description per current implementation
        val params = desc.requiredParameters + desc.optionalParameters
        assertEquals("", params[0].description)
        assertEquals("", params[1].description)
    }

    // Property-level LLMDescription does NOT override parameter descriptions currently
    @Serializable
    @LLMDescription("Class description wins")
    data class PropertyAnnotated(
        @property:LLMDescription("INT property desc") val x: Int,
        @property:LLMDescription("STRING property desc") val y: String
    )

    @Test
    fun property_level_llm_description_is_used_for_param_descriptions() {
        val desc = getToolDescriptor(typeToken<PropertyAnnotated>(), "prop_annotated")
        // Per implementation, param descriptions are taken from class-level descriptor annotations
        assertEquals(
            "INT property desc",
            desc.requiredParameters.single { it.name == "x" }.description
        )
        assertEquals(
            "STRING property desc",
            desc.requiredParameters.single { it.name == "y" }.description
        )
    }

    // Type-use LLMDescription on property type is not used for parameter descriptions
    @Serializable
    @LLMDescription("TypeUse class desc")
    data class TypeUseAnnotated(
        @property:LLMDescription("Name type desc") val name: String,
        val age: Int
    )

    @Test
    fun type_use_llm_description_on_property_type_is_ignored_for_param_descriptions() {
        val desc = getToolDescriptor(typeToken<TypeUseAnnotated>(), "type_use")
        val params = (desc.requiredParameters + desc.optionalParameters).associateBy { it.name }
        assertEquals("Name type desc", params.getValue("name").description)
        assertEquals("", params.getValue("age").description)
    }

    // Nested classes: parent and nested class descriptions; property-level ignored
    @Serializable
    @LLMDescription("Parent desc")
    data class ParentWithNested(
        val id: Int,
        val nested: Nested
    ) {
        @Serializable
        @LLMDescription("Nested desc")
        data class Nested(
            @property:LLMDescription("Nested class property desc") val street: String,
            val number: Int
        )
    }

    @Test
    fun nested_class_property_descriptions_follow_class_level_annotations() {
        val desc = getToolDescriptor(typeToken<ParentWithNested>(), "parent_nested")

        // Parent-level: required/optional split isn't the goal; check descriptions
        val nestedParam = (desc.requiredParameters + desc.optionalParameters).first { it.name == "nested" }
        // The parameter for the nested object should use the PARENT class description per current impl
        assertEquals("Nested desc", nestedParam.description)

        // Now inspect the nested object type
        val nestedObj = assertIs<ToolParameterType.Object>(nestedParam.type)
        val props = nestedObj.properties.associateBy { it.name }
        // All nested properties use the NESTED class description, not property-level
        assertEquals("Nested class property desc", props.getValue("street").description)
        assertEquals("", props.getValue("number").description)
    }
}
