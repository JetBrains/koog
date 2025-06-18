package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalAgentToolsApi::class)
object Enabler: DirectToolCallsEnabler

@OptIn(InternalAgentToolsApi::class)
class ToolTest {
    // Unstructured tool

    private object UnstructuredTool : SimpleTool<ToolArgs.Empty>() {
        override val argsSerializer = ToolArgs.Empty.serializer()

        override val descriptor = ToolDescriptor(
            name = "unstructured_tool",
            description = "Unstructured tool"
        )

        override suspend fun doExecute(args: ToolArgs.Empty): String = "Simple result"
    }

    @Test
    fun testSimpleUnstructuredToolSerialization() = runTest {
        val args = JsonObject(emptyMap())
        val result = UnstructuredTool.execute(UnstructuredTool.decodeArgs(args), Enabler)

        assertEquals("Simple result", result.toStringDefault())
    }

    // Structured tool

    private object SampleStructuredTool : Tool<SampleStructuredTool.Args, SampleStructuredTool.Result>(){
        @Serializable
        data class Args(val arg1: String, val arg2: Int) : ToolArgs

        @Serializable
        data class Result(val first: String, val second: Int) : ToolResult {
            override fun toStringDefault(): String = ToolJson.encodeToString(serializer(), this)
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "structured_tool",
            description = "Structured tool",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "arg1",
                    description = "arg1",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "arg2",
                    description = "arg2",
                    type = ToolParameterType.Integer
                )
            )
        )

        override suspend fun execute(args: Args): Result = Result("result", 1)
    }

    @Test
    fun testStructuredToolSerialization() = runTest {
        val args = buildJsonObject {
            put("arg1", "argument")
            put("arg2", 15)
        }
        val result = SampleStructuredTool.execute(SampleStructuredTool.decodeArgs(args), Enabler)

        assertEquals(
            //language=JSON
            expected = """{"first":"result","second":1}""",
            actual = result.toStringDefault()
        )
    }

    // Custom format tool

    private abstract class CustomFormatSerializer<T : ToolResult> : KSerializer<T> {
        final override val descriptor = PrimitiveSerialDescriptor("CustomFormat", PrimitiveKind.STRING)

        abstract fun toCustomFormat(value: T): String

        final override fun serialize(encoder: Encoder, value: T) {
            encoder.encodeString(toCustomFormat(value))
        }

        final override fun deserialize(decoder: Decoder): T {
            throw UnsupportedOperationException("Deserialization is not supported")
        }
    }

    private object CustomFormatTool : Tool<ToolArgs.Empty, CustomFormatTool.Result>() {
        @Serializable
        data class Result(val foo: String, val bar: String) : ToolResult {
            override fun toStringDefault(): String = "Foo: $foo | Bar: $bar"
        }

        override val argsSerializer = ToolArgs.Empty.serializer()

        override val descriptor = ToolDescriptor(
            name = "custom_format_tool",
            description = "Custom format tool",
        )

        override suspend fun execute(args: ToolArgs.Empty): Result {
            return Result("first result", "second result")
        }
    }

    @Test
    fun testCustomFormatToolSerialization() = runTest {
        val args = JsonObject(emptyMap())
        val result = CustomFormatTool.execute(CustomFormatTool.decodeArgs(args), Enabler)
        assertEquals("Foo: first result | Bar: second result", result.toStringDefault())
    }
}
