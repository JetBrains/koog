package ai.koog.agents.schema

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.schema.JavaTestFunction
import ai.koog.agents.core.tools.schema.getToolDescriptor
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionSchemaGenerationTest {
    @LLMDescription("A test class")
    data class TestClass(
        @property:LLMDescription("A string property")
        val stringProperty: String,
        val intProperty: Int,
        val longProperty: Long,
        val doubleProperty: Double,
        val floatProperty: Float,
        val booleanNullableProperty: Boolean?,
        val nullableProperty: String? = null,
        val listProperty: List<String> = emptyList(),
        val mapProperty: Map<String, Int> = emptyMap(),
        @property:LLMDescription("A custom nested property")
        val nestedProperty: NestedProperty = NestedProperty("foo", 1),
        val nestedListProperty: List<NestedProperty> = emptyList(),
        val nestedMapProperty: Map<String, NestedProperty> = emptyMap(),
        @property:LLMDescription("A custom polymorphic property")
        val polymorphicProperty: TestClosedPolymorphism = TestClosedPolymorphism.SubClass1("id1", "property1"),
        val enumProperty: TestEnum = TestEnum.One,
        val objectProperty: TestObject = TestObject,
    )

    @LLMDescription("Nested property class")
    data class NestedProperty(
        @property:LLMDescription("Nested foo property")
        val foo: String,
        val bar: Int
    )

    sealed class TestClosedPolymorphism {
        abstract val id: String

        @Suppress("unused")
        data class SubClass1(
            override val id: String,
            val property1: String
        ) : TestClosedPolymorphism()

        @Suppress("unused")
        data class SubClass2(
            override val id: String,
            val property2: Int,
        ) : TestClosedPolymorphism()
    }

    @Suppress("unused")
    enum class TestEnum {
        One,
        Two
    }

    data object TestObject

    @LLMDescription("Sample function")
    fun sampleFunction(
        @LLMDescription("Sample parameter")
        a: String,
        @LLMDescription("Another sample parameter")
        b: TestClass? = null,
    ): String {
        return ""
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testGeneratesToolDescriptorFromFunction() {
        val toolName = "test_tool"
        val toolDescription = "Test tool description"

        val nestedObject = ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor(
                    name = "foo",
                    description = "Nested foo property",
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "bar",
                    description = "",
                    type = ToolParameterType.Integer,
                ),
            ),
            requiredProperties = listOf("foo", "bar"),
            additionalProperties = false,
        )

        val testClass = ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor(
                    name = "stringProperty",
                    description = "A string property",
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "intProperty",
                    description = "",
                    type = ToolParameterType.Integer,
                ),
                ToolParameterDescriptor(
                    name = "longProperty",
                    description = "",
                    type = ToolParameterType.Integer,
                ),
                ToolParameterDescriptor(
                    name = "doubleProperty",
                    description = "",
                    type = ToolParameterType.Float,
                ),
                ToolParameterDescriptor(
                    name = "floatProperty",
                    description = "",
                    type = ToolParameterType.Float,
                ),
                ToolParameterDescriptor(
                    name = "booleanNullableProperty",
                    description = "",
                    type = ToolParameterType.AnyOf(
                        types = arrayOf(
                            ToolParameterDescriptor(type = ToolParameterType.Null, name = "", description = ""),
                            ToolParameterDescriptor(type = ToolParameterType.Boolean, name = "", description = ""),
                        )
                    )
                ),
                ToolParameterDescriptor(
                    name = "nullableProperty",
                    description = "",
                    type = ToolParameterType.AnyOf(
                        types = arrayOf(
                            ToolParameterDescriptor(type = ToolParameterType.Null, name = "", description = ""),
                            ToolParameterDescriptor(type = ToolParameterType.String, name = "", description = ""),
                        )
                    )
                ),
                ToolParameterDescriptor(
                    name = "listProperty",
                    description = "",
                    type = ToolParameterType.List(ToolParameterType.String),
                ),
                ToolParameterDescriptor(
                    name = "mapProperty",
                    description = "",
                    type = ToolParameterType.Object(
                        properties = emptyList(),
                        additionalProperties = true,
                        additionalPropertiesType = ToolParameterType.Integer,
                    )
                ),
                ToolParameterDescriptor(
                    name = "nestedProperty",
                    description = "A custom nested property",
                    type = nestedObject,
                ),
                ToolParameterDescriptor(
                    name = "nestedListProperty",
                    description = "",
                    type = ToolParameterType.List(
                        itemsType = nestedObject
                    )
                ),
                ToolParameterDescriptor(
                    name = "nestedMapProperty",
                    description = "",
                    type = ToolParameterType.Object(
                        properties = emptyList(),
                        requiredProperties = emptyList(),
                        additionalProperties = true,
                        additionalPropertiesType = nestedObject,
                    )
                ),
                ToolParameterDescriptor(
                    name = "polymorphicProperty",
                    description = "A custom polymorphic property",
                    type = ToolParameterType.AnyOf(
                        types = arrayOf(
                            ToolParameterDescriptor(
                                type = ToolParameterType.Object(
                                    properties = listOf(
                                        ToolParameterDescriptor(
                                            name = "id",
                                            description = "",
                                            type = ToolParameterType.String,
                                        ),
                                        ToolParameterDescriptor(
                                            name = "property1",
                                            description = "",
                                            type = ToolParameterType.String,
                                        )
                                    ),
                                    requiredProperties = listOf("id", "property1"),
                                    additionalProperties = false,
                                ),
                                name = "",
                                description = "",
                            ),
                            ToolParameterDescriptor(
                                type = ToolParameterType.Object(
                                    properties = listOf(
                                        ToolParameterDescriptor(
                                            name = "id",
                                            description = "",
                                            type = ToolParameterType.String,
                                        ),
                                        ToolParameterDescriptor(
                                            name = "property2",
                                            description = "",
                                            type = ToolParameterType.Integer,
                                        )
                                    ),
                                    requiredProperties = listOf("id", "property2"),
                                    additionalProperties = false,
                                ),
                                name = "",
                                description = "",
                            ),
                        )
                    )
                ),
                ToolParameterDescriptor(
                    name = "enumProperty",
                    description = "",
                    type = ToolParameterType.Enum(arrayOf("One", "Two")),
                ),
                ToolParameterDescriptor(
                    name = "objectProperty",
                    description = "",
                    type = ToolParameterType.Object(
                        properties = emptyList(),
                        additionalProperties = false,
                    ),
                )
            ),
            requiredProperties = listOf(
                "stringProperty",
                "intProperty",
                "longProperty",
                "doubleProperty",
                "floatProperty",
                "booleanNullableProperty",
                "nullableProperty",
                "listProperty",
                "mapProperty",
                "nestedProperty",
                "nestedListProperty",
                "nestedMapProperty",
                "polymorphicProperty",
                "enumProperty",
                "objectProperty",
            ),
            additionalProperties = false,
        )

        val expectedDescriptor = ToolDescriptor(
            name = toolName,
            description = toolDescription,
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "a",
                    description = "Sample parameter",
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "Another sample parameter",
                    type = ToolParameterType.AnyOf(
                        types = arrayOf(
                            ToolParameterDescriptor(type = ToolParameterType.Null, name = "", description = ""),
                            ToolParameterDescriptor(type = testClass, name = "", description = ""),
                        )
                    )
                )
            )
        )

        val actualDescriptor = getToolDescriptor(
            callable = ::sampleFunction,
            toolName = toolName,
            toolDescription = toolDescription,
        )

        assertEquals(expectedDescriptor, actualDescriptor)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testGeneratesToolDescriptorFromJavaFunction() {
        val toolName = "test_tool"
        val toolDescription = "Test tool description"

        val nestedObject = ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor(
                    name = "foo",
                    description = "Nested foo property",
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "bar",
                    description = "",
                    type = ToolParameterType.Integer,
                ),
            ),
            requiredProperties = listOf("foo", "bar"),
            additionalProperties = false,
        )

        val javaTestClass = ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor(
                    name = "stringProperty",
                    description = "A string property",
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "intProperty",
                    description = "",
                    type = ToolParameterType.Integer,
                ),
                ToolParameterDescriptor(
                    name = "longProperty",
                    description = "",
                    type = ToolParameterType.Integer,
                ),
                ToolParameterDescriptor(
                    name = "doubleProperty",
                    description = "",
                    type = ToolParameterType.Float,
                ),
                ToolParameterDescriptor(
                    name = "floatProperty",
                    description = "",
                    type = ToolParameterType.Float,
                ),
                ToolParameterDescriptor(
                    name = "booleanNullableProperty",
                    description = "",
                    type = ToolParameterType.Boolean,
                ),
                ToolParameterDescriptor(
                    name = "nullableProperty",
                    description = "",
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "listProperty",
                    description = "",
                    type = ToolParameterType.List(ToolParameterType.String),
                ),
                ToolParameterDescriptor(
                    name = "mapProperty",
                    description = "",
                    type = ToolParameterType.Object(
                        properties = emptyList(),
                        additionalProperties = true,
                        additionalPropertiesType = ToolParameterType.Integer,
                    )
                ),
                ToolParameterDescriptor(
                    name = "nestedProperty",
                    description = "A custom nested property",
                    type = nestedObject,
                ),
                ToolParameterDescriptor(
                    name = "nestedListProperty",
                    description = "",
                    type = ToolParameterType.List(
                        itemsType = nestedObject
                    )
                ),
                ToolParameterDescriptor(
                    name = "nestedMapProperty",
                    description = "",
                    type = ToolParameterType.Object(
                        properties = emptyList(),
                        requiredProperties = emptyList(),
                        additionalProperties = true,
                        additionalPropertiesType = nestedObject,
                    )
                ),
                ToolParameterDescriptor(
                    name = "enumProperty",
                    description = "",
                    type = ToolParameterType.Enum(arrayOf("One", "Two")),
                ),
            ),
            requiredProperties = listOf(
                "stringProperty",
                "intProperty",
                "longProperty",
                "doubleProperty",
                "floatProperty",
                "booleanNullableProperty",
                "nullableProperty",
                "listProperty",
                "mapProperty",
                "nestedProperty",
                "nestedListProperty",
                "nestedMapProperty",
                "enumProperty",
            ),
            additionalProperties = false,
        )

        val expectedDescriptor = ToolDescriptor(
            name = toolName,
            description = toolDescription,
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "a",
                    description = "Sample parameter",
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "Another sample parameter",
                    type = javaTestClass,
                )
            )
        )

        val actualDescriptor = getToolDescriptor(
            callable = JavaTestFunction.FUNCTION.kotlinFunction as KFunction<*>,
            toolName = toolName,
            toolDescription = toolDescription,
        )

        assertEquals(expectedDescriptor, actualDescriptor)
    }
}
