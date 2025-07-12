package ai.koog.prompt

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.TestInstance
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractPromptTest {

    val ts: Instant = Instant.parse("2023-01-01T00:00:00Z")

    val testClock: Clock = object : Clock {
        override fun now(): Instant = ts
    }

    val testRespMetaInfo = ResponseMetaInfo.create(testClock)
    val testReqMetaInfo = RequestMetaInfo.create(testClock)

    val promptId = "test-id"
    val systemMessage = "You are a helpful assistant with many capabilities"
    val assistantMessage = "I'm here to help!"
    val userMessage = "Can you help me calculate 5 + 3?"
    val speculationMessage = "The result is 8"
    val toolCallId = "tool_call_123"
    val toolName = "calculator"
    // language=json
    val toolCallContent = """{"operation": "add", "a": 5, "b": 3}"""
    val toolResultContent = "8"
    val finishReason = "stop"
    val emptyName = ""

    val simpleSchemaName = "simple-schema"
    val simpleSchema = buildJsonObject {
        put("type", "string")
    }

    val fullSchemaName = "full-schema"
    val fullSchema = buildJsonObject {
        put("type", "object")
        put("required", true)
    }

    val basicPrompt = Prompt.build("test", clock = testClock) {
        system(systemMessage)
        user(userMessage)
        message(
            Message.Assistant(
                content = assistantMessage,
                metaInfo = testRespMetaInfo,
                finishReason = finishReason
            )
        )
        tool {
            call(toolCallId, toolName, toolCallContent)
            result(toolCallId, toolName, toolResultContent)
        }
    }

    fun toolChoiceSerializationProvider(): Stream<Array<Any>> = Stream.of(
        arrayOf("Auto", LLMParams.ToolChoice.Auto),
        arrayOf("Required", LLMParams.ToolChoice.Required),
        arrayOf("Named", LLMParams.ToolChoice.Named(toolName)),
        arrayOf("None", LLMParams.ToolChoice.None)
    )

    fun schemaSerializationProvider(): Stream<Array<Any>> = Stream.of(
        arrayOf(
            "Simple JSON Schema",
            LLMParams.Schema.JSON.Simple(simpleSchemaName, simpleSchema),
            simpleSchemaName,
            LLMParams.Schema.JSON.Simple::class.java
        ),
        arrayOf(
            "Full JSON Schema",
            LLMParams.Schema.JSON.Full(fullSchemaName, fullSchema),
            fullSchemaName,
            LLMParams.Schema.JSON.Full::class.java
        )
    )
}
