package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
class SerialToToolDescriptionTest {

    // ---------- Helper models ----------

    @Serializable
    @LLMDescription("Person description")
    data class Person(
        val name: String,
        val age: Int = 0, // optional due to default
        val nickname: String? = null, // optional due to default
        val address: Address, // required
    )

    @Serializable
    data class Address(
        val street: String
    )

    @Serializable
    enum class Color { RED, GREEN, BLUE }

    @Serializable
    object Singleton

    @Serializable
    data class FreeFormHolder(
        @Contextual val meta: Any? = null // contextual => free-form property mapping
    )

    // ---------- Tests ----------

    @Test
    fun primitive_mappings_are_wrapped_as_value_parameter() {
        fun assertValueParam(descriptor: ToolDescriptor, expectedType: ToolParameterType) {
            assertEquals("value", descriptor.requiredParameters.single().name)
            assertEquals(0, descriptor.optionalParameters.size)
            assertEquals(expectedType, descriptor.requiredParameters.single().type)
        }

        // String
        assertValueParam(
            descriptor = String.serializer().descriptor.toolDescription("str"),
            expectedType = ToolParameterType.String
        )

        // Char -> String
        assertValueParam(
            descriptor = Char.serializer().descriptor.toolDescription("char"),
            expectedType = ToolParameterType.String
        )

        // Boolean
        assertValueParam(
            descriptor = Boolean.serializer().descriptor.toolDescription("bool"),
            expectedType = ToolParameterType.Boolean
        )

        // Integer family
        assertValueParam(
            descriptor = Int.serializer().descriptor.toolDescription("int"),
            expectedType = ToolParameterType.Integer
        )
        assertValueParam(
            descriptor = Long.serializer().descriptor.toolDescription("long"),
            expectedType = ToolParameterType.Integer
        )
        assertValueParam(
            descriptor = Short.serializer().descriptor.toolDescription("short"),
            expectedType = ToolParameterType.Integer
        )
        assertValueParam(
            descriptor = Byte.serializer().descriptor.toolDescription("byte"),
            expectedType = ToolParameterType.Integer
        )

        // Float family
        assertValueParam(
            descriptor = Float.serializer().descriptor.toolDescription("float"),
            expectedType = ToolParameterType.Float
        )
        assertValueParam(
            descriptor = Double.serializer().descriptor.toolDescription("double"),
            expectedType = ToolParameterType.Float
        )
    }

    @Test
    fun list_and_nested_list_mappings() {
        // List<Int>
        val listOfInt = ListSerializer(Int.serializer()).descriptor.toolDescription("ints")
        val listType = listOfInt.requiredParameters.single().type
        assertIs<ToolParameterType.List>(listType)
        assertEquals(ToolParameterType.Integer, (listType as ToolParameterType.List).itemsType)

        // List<List<String>>
        val nested = ListSerializer(ListSerializer(String.serializer())).descriptor.toolDescription("nested")
        val nestedType = nested.requiredParameters.single().type
        val outer = assertIs<ToolParameterType.List>(nestedType)
        val inner = assertIs<ToolParameterType.List>(outer.itemsType)
        assertEquals(ToolParameterType.String, inner.itemsType)
    }

    @Test
    fun enum_mapping_uses_element_names() {
        val enumDesc = Color.serializer().descriptor.toolDescription("color")
        val valueType = enumDesc.requiredParameters.single().type
        val enumType = assertIs<ToolParameterType.Enum>(valueType)
        assertEquals(arrayOf("RED", "GREEN", "BLUE").toList(), enumType.entries.toList())
    }

    @Test
    fun class_mapping_collects_required_and_optional_and_uses_class_description_for_fields() {
        val personDesc = Person.serializer().descriptor.toolDescription("person")

        // Top-level tool info
        assertEquals("person", personDesc.name)
        assertEquals("Person description", personDesc.description)

        // Required vs optional
        val requiredNames = personDesc.requiredParameters.map { it.name }.sorted()
        val optionalNames = personDesc.optionalParameters.map { it.name }.sorted()
        assertEquals(listOf("address", "name"), requiredNames)
        assertEquals(listOf("age", "nickname"), optionalNames)

        // Property descriptions currently mirror class-level description per implementation
        (personDesc.requiredParameters + personDesc.optionalParameters).forEach { param ->
            assertEquals("", param.description)
        }

        // Nested object type for address
        val addressParam = personDesc.requiredParameters.first { it.name == "address" }
        val addressType = assertIs<ToolParameterType.Object>(addressParam.type)
        val addressPropNames = addressType.properties.map { it.name }
        assertEquals(listOf("street"), addressPropNames)
        assertEquals(listOf("street"), addressType.requiredProperties)
        assertEquals(false, addressType.additionalProperties)
        assertEquals(null, addressType.additionalPropertiesType)
    }

    @Test
    fun object_and_map_are_free_form_tool_descriptors() {
        // Object
        val objectDesc = Singleton.serializer().descriptor.toolDescription("singleton")
        assertEquals("singleton", objectDesc.name)
        // description is empty since Singleton has no LLMDescription
        assertEquals("", objectDesc.description)
        assertTrue(objectDesc.requiredParameters.isEmpty())
        assertTrue(objectDesc.optionalParameters.isEmpty())

        // Map
        val mapDesc = MapSerializer(String.serializer(), Int.serializer()).descriptor.toolDescription("map")
        assertEquals("map", mapDesc.name)
        assertEquals("", mapDesc.description)
        assertTrue(mapDesc.requiredParameters.isEmpty())
        assertTrue(mapDesc.optionalParameters.isEmpty())
    }

    @Test
    fun contextual_property_maps_to_free_form_object_parameter_type() {
        val holderDesc = FreeFormHolder.serializer().descriptor.toolDescription("holder")
        val metaParam = holderDesc.optionalParameters.single { it.name == "meta" }
        val metaType = assertIs<ToolParameterType.Object>(metaParam.type)
        assertEquals(emptyList(), metaType.properties)
        assertEquals(emptyList(), metaType.requiredProperties)
        assertEquals(true, metaType.additionalProperties)
        assertEquals(ToolParameterType.String, metaType.additionalPropertiesType)
    }
}
