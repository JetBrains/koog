package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.reflect.ToolFromCallable.VarArgsSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KCallable
import kotlin.test.assertContentEquals

fun foo(i: Int, s: String, b: Boolean = true) = println("$i $s")
fun Any.fooEx(i: Int, s: String, b: Boolean = true) = println("$i $s")

class ReflectionArgsSerializerTest {

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        @JvmStatic
        fun getVariants(): Array<Arguments> = arrayOf(
            Arguments.of(::foo, /*language=JSON*/ """{ "b": false, "i": 10, "extra": "Extra" }""", mapOf("i" to 10, "b" to false)),
            Arguments.of(Any::fooEx, /*language=JSON*/ """{ "b": false, "i": 10, "extra": "Extra" }""", mapOf("i" to 10, "b" to false))
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
}