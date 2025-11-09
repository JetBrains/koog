package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
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
        // contextual => free-form property mapping
        @Contextual val meta: Any? = null
    )

    // ---------- Tests ----------

    @Test
    fun primitive_mappings_are_wrapped_as_value_parameter() {
        fun assertValueParam(descriptor: ToolDescriptor, expectedType: ToolParameterType) {
            assertEquals(toolWrapperValueKey, descriptor.requiredParameters.single().name)
            assertEquals(0, descriptor.optionalParameters.size)
            assertEquals(expectedType, descriptor.requiredParameters.single().type)
        }

        // String
        assertValueParam(
            descriptor = String.serializer().descriptor.asToolDescriptor("str"),
            expectedType = ToolParameterType.String
        )

        // Char -> String
        assertValueParam(
            descriptor = Char.serializer().descriptor.asToolDescriptor("char"),
            expectedType = ToolParameterType.String
        )

        // Boolean
        assertValueParam(
            descriptor = Boolean.serializer().descriptor.asToolDescriptor("bool"),
            expectedType = ToolParameterType.Boolean
        )

        // Integer family
        assertValueParam(
            descriptor = Int.serializer().descriptor.asToolDescriptor("int"),
            expectedType = ToolParameterType.Integer
        )
        assertValueParam(
            descriptor = Long.serializer().descriptor.asToolDescriptor("long"),
            expectedType = ToolParameterType.Integer
        )
        assertValueParam(
            descriptor = Short.serializer().descriptor.asToolDescriptor("short"),
            expectedType = ToolParameterType.Integer
        )
        assertValueParam(
            descriptor = Byte.serializer().descriptor.asToolDescriptor("byte"),
            expectedType = ToolParameterType.Integer
        )

        // Float family
        assertValueParam(
            descriptor = Float.serializer().descriptor.asToolDescriptor("float"),
            expectedType = ToolParameterType.Float
        )
        assertValueParam(
            descriptor = Double.serializer().descriptor.asToolDescriptor("double"),
            expectedType = ToolParameterType.Float
        )
    }

    @Test
    fun list_and_nested_list_mappings() {
        // List<Int>
        val listOfInt = ListSerializer(Int.serializer()).descriptor.asToolDescriptor("ints")
        val listType = listOfInt.requiredParameters.single().type
        assertIs<ToolParameterType.List>(listType)
        assertEquals(ToolParameterType.Integer, (listType as ToolParameterType.List).itemsType)

        // List<List<String>>
        val nested = ListSerializer(ListSerializer(String.serializer())).descriptor.asToolDescriptor("nested")
        val nestedType = nested.requiredParameters.single().type
        val outer = assertIs<ToolParameterType.List>(nestedType)
        val inner = assertIs<ToolParameterType.List>(outer.itemsType)
        assertEquals(ToolParameterType.String, inner.itemsType)
    }

    @Test
    fun enum_mapping_uses_element_names() {
        val enumDesc = Color.serializer().descriptor.asToolDescriptor("color")
        val valueType = enumDesc.requiredParameters.single().type
        val enumType = assertIs<ToolParameterType.Enum>(valueType)
        assertEquals(arrayOf("RED", "GREEN", "BLUE").toList(), enumType.entries.toList())
    }

    @Test
    fun class_mapping_collects_required_and_optional_and_uses_class_description_for_fields() {
        val personDesc = Person.serializer().descriptor.asToolDescriptor("person")

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
            assertEquals(param.name, param.description)
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
        val objectDesc = Singleton.serializer().descriptor.asToolDescriptor("singleton")
        assertEquals("singleton", objectDesc.name)
        // description is empty since Singleton has no LLMDescription
        assertEquals("", objectDesc.description)
        assertTrue(objectDesc.requiredParameters.isEmpty())
        assertTrue(objectDesc.optionalParameters.isEmpty())

        // Map
        val mapDesc = MapSerializer(String.serializer(), Int.serializer()).descriptor.asToolDescriptor("map")
        assertEquals("map", mapDesc.name)
        assertEquals("", mapDesc.description)
        assertTrue(mapDesc.requiredParameters.isEmpty())
        assertTrue(mapDesc.optionalParameters.isEmpty())
    }

    @Test
    fun contextual_property_maps_to_free_form_object_parameter_type() {
        val holderDesc = FreeFormHolder.serializer().descriptor.asToolDescriptor("holder")
        val metaParam = holderDesc.optionalParameters.single { it.name == "meta" }
        val metaType = assertIs<ToolParameterType.Object>(metaParam.type)
        assertEquals(emptyList(), metaType.properties)
        assertEquals(emptyList(), metaType.requiredProperties)
        assertEquals(true, metaType.additionalProperties)
        assertEquals(ToolParameterType.String, metaType.additionalPropertiesType)
    }

    @Serializable
    @LLMDescription(
        "Finish tool to compile final plan suggestion for the user's request. \n" +
            "Call to provide the final plan suggestion result."
    )
    data class TripPlan(
        @property:LLMDescription("The steps in the user travel plan.")
        val steps: List<Step>,
    ) {
        @Serializable
        @LLMDescription("The steps in the user travel plan.")
        data class Step(
            @property:LLMDescription("The location of the destination (e.g. city name)")
            val location: String,
            @property:LLMDescription("ISO 3166-1 alpha-2 country code of the location (e.g. US, GB, FR).")
            val countryCodeISO2: String? = null,
            @property:LLMDescription("Start date when the user arrives in this location in the ISO format, e.g. 2022-01-01.")
            val fromDate: LocalDate,
            @property:LLMDescription("End date when the user leaves this location in the ISO format, e.g. 2022-01-01.")
            val toDate: LocalDate,
            @property:LLMDescription("More information about this step from the plan")
            val description: String
        )
    }

    val expectedTripPlanToolDescriptor = ToolDescriptor(
        name = "provideTripPlan",
        description = """
            Finish tool to compile final plan suggestion for the user's request. 
            Call to provide the final plan suggestion result.
        """.trimIndent(),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "steps",
                description = "The steps in the user travel plan.",
                type = ToolParameterType.List(
                    ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor(
                                name = "location",
                                description = "The location of the destination (e.g. city name)",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "countryCodeISO2",
                                description = "ISO 3166-1 alpha-2 country code of the location (e.g. US, GB, FR).",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "fromDate",
                                description = "Start date when the user arrives in this location in the ISO format, e.g. 2022-01-01.",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "toDate",
                                description = "End date when the user leaves this location in the ISO format, e.g. 2022-01-01.",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "description",
                                description = "More information about this step from the plan",
                                type = ToolParameterType.String
                            )
                        ),
                        requiredProperties = listOf(
                            "location",
                            "fromDate",
                            "toDate",
                            "description"
                        ),
                        additionalProperties = false
                    )
                ),
            )
        )
    )

    @Test
    fun verify_class_with_array_of_nested_objects_tool_descriptor_generation() {
        val tripPlanDescriptor = serializer<TripPlan>().descriptor.asToolDescriptor("provideTripPlan")

        assertEquals(expectedTripPlanToolDescriptor, tripPlanDescriptor)
    }

    @Test
    fun verify_optional_description_applies() {
        val tripPlanDescriptor = serializer<TripPlan>().descriptor.asToolDescriptor(
            toolName = "provideTripPlan",
            toolDescription = "Custom tool ,call me!"
        )

        assertEquals("Custom tool ,call me!", tripPlanDescriptor.description)
        assertEquals(expectedTripPlanToolDescriptor.copy(description = "Custom tool ,call me!"), tripPlanDescriptor)
    }
}
