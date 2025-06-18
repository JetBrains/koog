@file:OptIn(InternalAgentToolsApi::class)

package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolFromCallable.VarArgsSerializer
import ai.koog.agents.core.tools.reflect.ToolsFromCallableTest.Companion.ToolsEnabler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KCallable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

fun foo(i: Int, s: String, b: Boolean = true) = println("$i $s")
fun Any.fooEx(i: Int, s: String, b: Boolean = true) = println("$i $s")

class ReflectionArgsSerializerTest {

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        @JvmStatic
        fun getVariants(): Array<Arguments> = arrayOf(
            Arguments.of(
                ::foo, /*language=JSON*/
                """{ "b": false, "i": 10, "extra": "Extra" }""",
                mapOf("i" to 10, "b" to false)
            ),
            Arguments.of(
                Any::fooEx, /*language=JSON*/
                """{ "b": false, "i": 10, "extra": "Extra" }""",
                mapOf("i" to 10, "b" to false)
            )
        )
    }

    @ParameterizedTest
    @MethodSource("getVariants")
    fun testArgsDeserialization(callable: KCallable<*>, argsJson: String, result: Map<String, Any?>) {
        val varArgsSerializer = VarArgsSerializer(callable)
        val decodedArguments = json.decodeFromString(varArgsSerializer, argsJson)
        assertContentEquals(
            result.entries.map { it.key to it.value }.sortedBy { it.first }.toList(),
            decodedArguments.asNamedValues().sortedBy { it.first }.toList()
        )
    }


    @Serializable
    data class MySpecificToolArgs(
        @LLMDescription("arg Long") val argLong: Long,
        @LLMDescription("arg Double") val argDouble: Double
    )

    class MySpecificTool() {
        @Tool
        @LLMDescription("Specific tool")
        suspend fun execute(@LLMDescription("arg Long") argLong: Long): String {
            return "Specific tool called with $argLong"
        }

        @Tool
        @LLMDescription("Specific tool without tool annotation")
        suspend fun executeDouble(@LLMDescription("arg Long") argDouble: Double): String {
            return "Specific tool called with $argDouble"
        }

        @Tool
        @LLMDescription("Specific tool without tool annotation")
        suspend fun executeWithArgs(
            @LLMDescription("args Tool") args: MySpecificToolArgs,
            @LLMDescription("args Tool") args2: MySpecificToolArgs
        ): String {
            return "Specific tool called with ${args.argLong} and ${args.argDouble}"
        }

        suspend fun executeWithListArg(@LLMDescription("args Tool") args: List<MySpecificToolArgs>): String {
            return "Specific tool called with ${args.joinToString(", ") { "${it.argLong} and ${it.argDouble}" }}"
        }
    }

    @Test
    fun testToolLongArg() {
        val toolClass = MySpecificTool()
        val tool = toolClass::execute.asTool(ToolsFromCallableTest.Companion.json)

        assertEquals(
            "Specific tool called with 42",
            runBlocking {
                val args = tool.decodeArgs(buildJsonObject { put("argLong", JsonPrimitive(42)) })
                val rawResult = tool.execute(args, ToolsEnabler)
                rawResult.result
            },
        )
    }

    @Test
    fun testToolDoubleArg() {
        val toolClass = MySpecificTool()
        val tool: ToolFromCallable = toolClass::executeDouble.asTool(ToolsFromCallableTest.Companion.json)

        assertEquals(
            "Specific tool called with 42.0",
            runBlocking {
                val args = tool.decodeArgs(buildJsonObject { put("argDouble", JsonPrimitive(42.0)) })
                val rawResult = tool.execute(args, ToolsEnabler)
                rawResult.result
            },
        )
    }

    @Test
    fun testToolWithArgs() {
        val toolClass = MySpecificTool()
        val tool = toolClass::executeWithArgs.asTool(ToolsFromCallableTest.Companion.json)

        assertEquals(
            "Specific tool called with 42 and 3.14",
            runBlocking {
                val args: ToolFromCallable.VarArgs =
                    tool.decodeArgs(buildJsonObject {
                        put(
                            "args",
                            buildJsonObject {
                                put("argLong", JsonPrimitive(42))
                                put("argDouble", JsonPrimitive(3.14))
                            })
                        put(
                            "args2",
                            buildJsonObject {
                                put("argLong", JsonPrimitive(42))
                                put("argDouble", JsonPrimitive(3.14))
                            })
                    })
                val rawResult = tool.execute(args, ToolsEnabler)
                rawResult.result
            }
        )
    }

    @Test
    fun testToolWithListArg() {
        val toolClass = MySpecificTool()
        val tool = toolClass::executeWithListArg.asTool(ToolsFromCallableTest.Companion.json)

        assertEquals(
            "Specific tool called with 42 and 3.14, 22 and 3.14",
            runBlocking {
                val args: ToolFromCallable.VarArgs =
                    tool.decodeArgs(buildJsonObject {
                        put(
                            "args",
                            buildJsonArray {
                                add(buildJsonObject {
                                    put("argLong", JsonPrimitive(42))
                                    put("argDouble", JsonPrimitive(3.14))
                                })
                                add(buildJsonObject {
                                    put("argLong", JsonPrimitive(22))
                                    put("argDouble", JsonPrimitive(3.14))
                                })
                            })
                    })
                val rawResult = tool.execute(args, ToolsEnabler)
                rawResult.result
            }
        )
    }
}