package ai.koog.agents.schema

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.schema.JavaTestClass
import ai.koog.agents.core.tools.schema.getToolDescriptor
import ai.koog.serialization.JavaTypeToken
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaClassSchemaGeneratorTest {
    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testGeneratesToolDescriptorFromJavaClass() {
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

        val expectedDescriptor = ToolDescriptor(
            name = toolName,
            description = toolDescription,
            requiredParameters = listOf(
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
            )
        )

        val actualDescriptor = getToolDescriptor(
            argsType = JavaTypeToken(JavaTestClass.TEST_CLASS),
            toolName = toolName,
            toolDescription = toolDescription,
        )

        assertEquals(expectedDescriptor, actualDescriptor)
    }
}
