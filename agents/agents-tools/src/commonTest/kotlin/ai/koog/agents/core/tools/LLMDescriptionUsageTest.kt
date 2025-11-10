package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
class LLMDescriptionUsageTest {

    // 1) Class-level LLMDescription is applied to ToolDescriptor and all fields
    @Serializable
    @LLMDescription("MyData description")
    data class MyData(
        val a: Int,
        val b: String?
    )

    @Test
    fun class_level_llm_description_applies_to_tool_and_fields() {
        val desc = MyData.serializer().descriptor.asToolDescriptor("my_data")

        // Tool-level description
        assertEquals("my_data", desc.name)
        assertEquals("MyData description", desc.description)

        // All parameters use class-level description per current implementation
        val params = desc.requiredParameters + desc.optionalParameters
        assertEquals("a", params[0].description)
        assertEquals("b", params[1].description)
    }

    // 2) Property-level LLMDescription does NOT override parameter descriptions currently
    @Serializable
    @LLMDescription("Class description wins")
    data class PropertyAnnotated(
        @property:LLMDescription("INT property desc") val x: Int,
        @property:LLMDescription("STRING property desc") val y: String
    )

    @Test
    fun property_level_llm_description_is_used_for_param_descriptions() {
        val desc = PropertyAnnotated.serializer().descriptor.asToolDescriptor("prop_annotated")
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

    // 3) Type-use LLMDescription on property type is not used for parameter descriptions
    @Serializable
    @LLMDescription("TypeUse class desc")
    data class TypeUseAnnotated(
        @property:LLMDescription("Name type desc") val name: String,
        val age: Int
    )

    @Test
    fun type_use_llm_description_on_property_type_is_ignored_for_param_descriptions() {
        val desc = TypeUseAnnotated.serializer().descriptor.asToolDescriptor("type_use")
        val params = (desc.requiredParameters + desc.optionalParameters).associateBy { it.name }
        assertEquals("Name type desc", params.getValue("name").description)
        assertEquals("age", params.getValue("age").description)
    }

    // 4) Enum-level LLMDescription is used for value-wrapped ToolDescriptor description
    @Serializable
    @LLMDescription("Color enum description")
    enum class DescribedColor { RED, GREEN }

    @Test
    fun enum_level_llm_description_applied_to_value_tool_descriptor() {
        val desc = DescribedColor.serializer().descriptor.asToolDescriptor("color")
        assertEquals("Color enum description", desc.description)
        // Required single "value" parameter of Enum type
        val param = desc.requiredParameters.single()
        assertEquals(toolWrapperValueKey, param.name)
        assertIs<ToolParameterType.Enum>(param.type)
    }

    // 5) Object-level LLMDescription is used for free-form ToolDescriptor description
    @Serializable
    @LLMDescription("Singleton object description")
    object DescribedSingleton

    @Test
    fun object_level_llm_description_applied_to_free_form_descriptor() {
        val desc = DescribedSingleton.serializer().descriptor.asToolDescriptor("singleton")
        assertEquals("Singleton object description", desc.description)
        assertTrue(desc.requiredParameters.isEmpty())
        assertTrue(desc.optionalParameters.isEmpty())
    }

    // 6) Nested classes: parent and nested class descriptions; property-level ignored
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
        val desc = ParentWithNested.serializer().descriptor.asToolDescriptor("parent_nested")

        // Parent-level: required/optional split isn't the goal; check descriptions
        val nestedParam = (desc.requiredParameters + desc.optionalParameters).first { it.name == "nested" }
        // The parameter for the nested object should use the PARENT class description per current impl
        assertEquals("nested", nestedParam.description)

        // Now inspect the nested object type
        val nestedObj = assertIs<ToolParameterType.Object>(nestedParam.type)
        val props = nestedObj.properties.associateBy { it.name }
        // All nested properties use the NESTED class description, not property-level
        assertEquals("Nested class property desc", props.getValue("street").description)
        assertEquals("number", props.getValue("number").description)
    }
}
