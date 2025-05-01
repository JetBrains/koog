package ai.grazie.code.agents.core.tools.serialization

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
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
                    type = ToolParameterType.String,
                    defaultValue = "hello"
                ),
                ToolParameterDescriptor(
                    name = "p2",
                    description = "blah blah",
                    type = ToolParameterType.Integer,
                    defaultValue = 15
                ),
                ToolParameterDescriptor(
                    name = "p3",
                    description = "blah blah",
                    type = ToolParameterType.Enum(MyEnum.entries, MyEnum.serializer()),
                    defaultValue = MyEnum.B
                ),
                ToolParameterDescriptor(
                    name = "p4",
                    description = "blah blah",
                    type = ToolParameterType.List<String>(ToolParameterType.String),
                    defaultValue = listOf("a")
                ),
                ToolParameterDescriptor(
                    name = "p5",
                    description = "blah blah",
                    type = ToolParameterType.List<Int>(ToolParameterType.Integer),
                    defaultValue = listOf(15)
                ),
                ToolParameterDescriptor(
                    name = "p6",
                    description = "blah blah",
                    type = ToolParameterType.List(
                        ToolParameterType.Enum(MyEnum.entries, MyEnum.serializer())
                    ),
                    defaultValue = listOf(MyEnum.A, MyEnum.B)
                ),
                ToolParameterDescriptor(
                    name = "p7",
                    description = "blah blah",
                    type = ToolParameterType.List(
                        ToolParameterType.List(
                            ToolParameterType.Enum(MyEnum.entries, MyEnum.serializer())
                        )
                    ),
                    defaultValue = listOf(listOf(MyEnum.C))
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
            [{"name":"tool-1","description":"really good tool!","required_parameters":[{"name":"p1","type":"STRING","description":"blah blah","default":"hello"},{"name":"p2","type":"INT","description":"blah blah","default":15},{"name":"p3","type":"ENUM","description":"blah blah","enum":["A","B","C","D"],"default":"B"},{"name":"p4","type":"ARRAY","description":"blah blah","items":{"type":"STRING"},"default":["a"]},{"name":"p5","type":"ARRAY","description":"blah blah","items":{"type":"INT"},"default":[15]},{"name":"p6","type":"ARRAY","description":"blah blah","items":{"type":"ENUM","enum":["A","B","C","D"]},"default":["A","B"]},{"name":"p7","type":"ARRAY","description":"blah blah","items":{"type":"ARRAY","items":{"type":"ENUM","enum":["A","B","C","D"]}},"default":[["C"]]}],"optional_parameters":[]}]
            """.trimIndent(),
            actual = serializeToolDescriptorsToJsonString(toolDescriptors)
        )
    }
}