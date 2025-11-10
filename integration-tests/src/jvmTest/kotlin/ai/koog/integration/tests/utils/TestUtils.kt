package ai.koog.integration.tests.utils

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

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
        substrings.any { needle -> msg.contains(needle, ignoreCase = true) }.shouldBeTrue()
    }

    fun assertResponseContainsToolCall(response: List<Message>, toolName: String) {
        with(response) {
            shouldNotBeEmpty()
            shouldForAny { it is Message.Tool.Call }
            toolName shouldBe (first { it is Message.Tool.Call } as Message.Tool.Call).tool
        }
    }

    fun isValidJson(str: String): Boolean = try {
        Json.parseToJsonElement(str)
        true
    } catch (_: Exception) {
        false
    }
}
