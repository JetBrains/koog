package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
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
object Enabler : DirectToolCallsEnabler

@OptIn(InternalAgentToolsApi::class)
class ToolTest {
    // Unstructured tool

    private object UnstructuredTool : SimpleTool<Unit>() {
        override val argsSerializer = Unit.serializer()

        override val name: String = "unstructured_tool"
        override val description: String = "Unstructured tool"

        override suspend fun doExecute(args: Unit): String = "Simple result"
    }

    @Test
    fun testSimpleUnstructuredToolSerialization() = runTest {
        val args = JsonObject(emptyMap())
        val result = UnstructuredTool.execute(UnstructuredTool.decodeArgs(args), Enabler)

        assertEquals("Simple result", result)
    }

    // Structured tool

    private object SampleStructuredTool : Tool<SampleStructuredTool.Args, SampleStructuredTool.Result>() {
        @Serializable
        data class Args(val arg1: String, val arg2: Int)

        @Serializable
        data class Result(val first: String, val second: Int)

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "structured_tool"
        override val description: String = "Structured tool"

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
            actual = SampleStructuredTool.encodeResultToStringUnsafe(result)
        )
    }

    // Custom format tool

    private abstract class CustomFormatSerializer<T> : KSerializer<T> {
        final override val descriptor = PrimitiveSerialDescriptor("CustomFormat", PrimitiveKind.STRING)

        abstract fun toCustomFormat(value: T): String

        final override fun serialize(encoder: Encoder, value: T) {
            encoder.encodeString(toCustomFormat(value))
        }

        final override fun deserialize(decoder: Decoder): T {
            throw UnsupportedOperationException("Deserialization is not supported")
        }
    }

    private object CustomFormatTool : Tool<Unit, CustomFormatTool.Result>() {
        @Serializable
        data class Result(val foo: String, val bar: String) : ToolResult {
            override fun toStringDefault(): String = "Foo: $foo | Bar: $bar"
        }

        override val argsSerializer = Unit.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "custom_format_tool"
        override val description: String = "Custom format tool"

        override suspend fun execute(args: Unit): Result {
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
