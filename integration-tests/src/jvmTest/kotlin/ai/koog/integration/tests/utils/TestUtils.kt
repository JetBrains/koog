package ai.koog.integration.tests.utils

import ai.koog.prompt.llm.GoogleLLMProvider
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
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
        if (provider !is GoogleLLMProvider) {
            // Google response_schema does not support additionalProperties at the root
            put("additionalProperties", JsonPrimitive(false))
        }
    }

    fun assertExceptionMessageContains(ex: Throwable, vararg substrings: String) {
        val msg = ex.message ?: ""
        substrings.any { needle -> msg.contains(needle, ignoreCase = true) }.shouldBeTrue()
    }

    fun assertResponseContainsToolCall(response: Message.Assistant, toolName: String) {
        with(response) {
            parts.shouldForAny { (it is MessagePart.Tool.Call) && it.tool == toolName }
        }
    }

    fun assertResponseContainsTextAndToolCall(response: Message.Assistant, toolName: String): MessagePart.Tool.Call {
        response.parts.shouldForAny { it is MessagePart.Text && it.text.isNotBlank() }
        response.parts.shouldForAny { it is MessagePart.Tool.Call && it.tool == toolName }
        return response.parts.filterIsInstance<MessagePart.Tool.Call>().first { it.tool == toolName }
    }

    fun assertToolResultCorrelatesWithCall(
        toolCall: MessagePart.Tool.Call,
        toolResult: MessagePart.Tool.Result,
    ) {
        toolResult.id shouldBe toolCall.id
        toolResult.tool shouldBe toolCall.tool
    }

    fun assertResponseDoesNotLeakRequestAttachments(response: Message.Assistant) {
        response.parts.any { it is MessagePart.Attachment } shouldBe false
    }

    fun isValidJson(str: String): Boolean = try {
        Json.parseToJsonElement(str)
        true
    } catch (_: Exception) {
        false
    }

    fun assertResponseContainsReasoning(response: Message.Assistant, checkMetaInfo: Boolean = true) {
        with(response) {
            parts.shouldForAny { it is MessagePart.Reasoning }
            if (checkMetaInfo) {
                metaInfo.shouldNotBeNull {
                    inputTokensCount.shouldNotBeNull { shouldBeGreaterThan(0) }
                    outputTokensCount.shouldNotBeNull { shouldBeGreaterThan(0) }
                    totalTokensCount.shouldNotBeNull { shouldBeGreaterThan(0) }
                }
            }
        }
    }

    fun assertResponseContainsReasoningWithEncryption(response: Message.Assistant) {
        with(response) {
            parts.filterIsInstance<MessagePart.Reasoning>().firstOrNull().shouldNotBeNull {
                content.shouldNotBeEmpty()
                encrypted
                    .shouldNotBeNull()
                    .shouldNotBeEmpty()
            }
        }
    }
}
