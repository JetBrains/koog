package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolSerializationTest {
    @Serializable
    enum class MyEnum { A, B, C, D }

    val toolDescriptors = listOf(
        ToolDescriptor(
            name = "tool-1",
            description = "really good tool!",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "p1",
                    description = "blah blah",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "p2",
                    description = "blah blah",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "p3",
                    description = "blah blah",
                    type = ToolParameterType.Enum(MyEnum.entries)
                ),
                ToolParameterDescriptor(
                    name = "p4",
                    description = "blah blah",
                    type = ToolParameterType.List(ToolParameterType.String)
                ),
                ToolParameterDescriptor(
                    name = "p5",
                    description = "blah blah",
                    type = ToolParameterType.List(ToolParameterType.Integer)
                ),
                ToolParameterDescriptor(
                    name = "p6",
                    description = "blah blah",
                    type = ToolParameterType.List(
                        ToolParameterType.Enum(MyEnum.entries)
                    )
                ),
                ToolParameterDescriptor(
                    name = "p7",
                    description = "blah blah",
                    type = ToolParameterType.List(
                        ToolParameterType.List(
                            ToolParameterType.Enum(MyEnum.entries)
                        )
                    )
                ),
            ),
            optionalParameters = emptyList()
        )
    )

    @Test
    fun testToolDescriptorsSerialization() {
        assertEquals(
            //language=JSON
            expected = """
            [{"name":"tool-1","description":"really good tool!","required_parameters":[{"name":"p1","type":"STRING","description":"blah blah"},{"name":"p2","type":"INT","description":"blah blah"},{"name":"p3","type":"ENUM","description":"blah blah","enum":["A","B","C","D"]},{"name":"p4","type":"ARRAY","description":"blah blah","items":{"type":"STRING"}},{"name":"p5","type":"ARRAY","description":"blah blah","items":{"type":"INT"}},{"name":"p6","type":"ARRAY","description":"blah blah","items":{"type":"ENUM","enum":["A","B","C","D"]}},{"name":"p7","type":"ARRAY","description":"blah blah","items":{"type":"ARRAY","items":{"type":"ENUM","enum":["A","B","C","D"]}}}],"optional_parameters":[]}]
            """.trimIndent(),
            actual = serializeToolDescriptorsToJsonString(toolDescriptors)
        )
    }
}