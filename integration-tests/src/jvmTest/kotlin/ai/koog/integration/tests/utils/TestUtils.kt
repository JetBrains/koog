package ai.koog.integration.tests.utils

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object TestUtils {
    fun singlePropertyObjectSchema(provider: LLMProvider, propName: String, type: String) = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put(
            "properties",
            buildJsonObject {
                put(propName, buildJsonObject { put("type", JsonPrimitive(type)) })
            }
        )
        put("required", buildJsonArray { add(JsonPrimitive(propName)) })
        if (provider !is LLMProvider.Google) {
            // Google response_schema does not support additionalProperties at the root
            put("additionalProperties", JsonPrimitive(false))
        }
    }

    fun assertExceptionMessageContains(ex: Throwable, vararg substrings: String) {
        val msg = ex.message ?: ""
        val matches = substrings.any { needle -> msg.contains(needle, ignoreCase = true) }
        assertTrue(matches, "Exception message doesn't contain expected error: ${ex.message}")
    }

    fun assertResponseContainsToolCall(response: List<Message>, toolName: String) {
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.any { it is Message.Tool.Call }, "Response should contain a tool call")
        val toolCall = response.first { it is Message.Tool.Call } as Message.Tool.Call
        assertEquals(toolName, toolCall.tool, "Tool name should be $toolName")
    }

    fun isValidJson(str: String): Boolean = try {
        Json.parseToJsonElement(str)
        true
    } catch (_: Exception) {
        false
    }
}
