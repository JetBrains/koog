package ai.koog.agents.core.tools

import ai.koog.serialization.jackson.JacksonSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaToolTest {
    val serializer = JacksonSerializer()

    @Test
    fun testJavaToolExecutesInSuspendableContext() = runTest {
        val tool: Tool<ThinkJavaTool.Args, ThinkJavaTool.Result> = ThinkJavaTool()

        val argsSerialized = buildJsonObject {
            put("thought", "A thought")
        }.toKoogJSONObject()

        val resultSerialized = buildJsonObject {
            put("outcome", "A thought")
        }.toKoogJSONObject()

        val args = tool.decodeArgs(argsSerialized, serializer)
        val result = tool.execute(args)
        val actualResultSerialized = tool.encodeResult(result, serializer)

        assertEquals(resultSerialized, actualResultSerialized)
    }
}
