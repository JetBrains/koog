package ai.koog.prompt

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class PromptTest : AbstractPromptTest() {

    @Test
    fun testPromptBuilding() {
        val assistantMessage = "Hi! How can I help you?"
        val toolCallId = "tool_call_dummy_123"
        val toolName = "search"
        val toolContent = "Searching for information..."
        val toolResult = "Found some results"

        val prompt = Prompt.build("test") {
            system(systemMessage)
            user(userMessage)
            assistant(assistantMessage)
            tool {
                call(toolCallId, toolName, toolContent)
                result(toolCallId, toolName, toolResult)
            }
        }

        assertSoftly(prompt.messages) {
            size shouldBe 5
            get(0).shouldBeInstanceOf<Message.System> {
                it.content shouldBe systemMessage
            }
            get(1).shouldBeInstanceOf<Message.User> {
                it.content shouldBe userMessage
            }
            get(2).shouldBeInstanceOf<Message.Assistant> {
                it.content shouldBe assistantMessage
            }
            get(3).shouldBeInstanceOf<Message.Tool.Call> {
                it.id shouldBe toolCallId
                it.tool shouldBe toolName
                it.content shouldBe toolContent
            }
            get(4).shouldBeInstanceOf<Message.Tool.Result> {
                it.id shouldBe toolCallId
                it.tool shouldBe toolName
                it.content shouldBe toolResult
            }
        }
    }

    @Test
    fun testUpdatePromptWithNewMessages() {
        val systemMessage = "You are a coding assistant"
        val userMessage = "Help me with Kotlin"
        val assistantMessage = "I'll help you with Kotlin programming"

        val newMessages = listOf(
            Message.System(systemMessage, testReqMetaInfo),
            Message.User(userMessage, testReqMetaInfo),
            Message.Assistant(assistantMessage, testRespMetaInfo)
        )

        val updatedPrompt = basicPrompt.withMessages { newMessages }

        assertSoftly(updatedPrompt.messages) {
            size shouldBe 3
            get(0).content shouldBe systemMessage
            get(1).content shouldBe userMessage
            get(2).content shouldBe assistantMessage
        }
    }

    @Test
    fun testUpdatePromptWithNewParams() {
        val speculation = "test speculation"
        val schemaName = "test-schema"
        val newParams = LLMParams(
            temperature = 0.7,
            speculation = speculation,
            schema = LLMParams.Schema.JSON.Simple(
                schemaName,
                buildJsonObject { put("type", "string") }
            ),
            toolChoice = LLMParams.ToolChoice.Auto,
            user = "test_user"
        )

        val updatedPrompt = basicPrompt.withParams(newParams)

        assertSoftly(updatedPrompt.params) {
            temperature shouldBe 0.7
            this.speculation shouldBe speculation
            withClue("Schema should be of type Simple") {
                schema.shouldBeInstanceOf<LLMParams.Schema.JSON.Simple>()
            }
            schema?.name shouldBe schemaName
            toolChoice shouldBe LLMParams.ToolChoice.Auto
            user shouldBe "test_user"
        }
    }

    @Test
    fun testUpdatePromptWithUpdatedParams() {
        val newSpeculation = "improved speculation"
        val schemaName = "full-schema"
        val updatedPrompt = basicPrompt.withUpdatedParams {
            temperature = 0.8
            speculation = newSpeculation
            schema = LLMParams.Schema.JSON.Full(
                schemaName,
                buildJsonObject {
                    put("type", "object")
                    put("required", true)
                }
            )
            toolChoice = LLMParams.ToolChoice.Required
            user = "updated_user"
        }

        assertSoftly(updatedPrompt.params) {
            temperature shouldBe 0.8
            this.speculation shouldBe newSpeculation
            withClue("Schema should be of type Full") {
                schema.shouldBeInstanceOf<LLMParams.Schema.JSON.Full>()
            }
            schema?.name shouldBe schemaName
            withClue("ToolChoice should be Required") {
                toolChoice.shouldBeInstanceOf<LLMParams.ToolChoice.Required>()
            }
            user shouldBe "updated_user"
        }
    }

    @Test
    fun testWithMessagesFunctions() {
        val originalPrompt = Prompt.build("test") {
            system("You are a helpful assistant")
            user("Hello")
        }

        // Test adding a message
        val updatedPrompt = originalPrompt.withMessages { messages ->
            messages + Message.Assistant("How can I help you?", testRespMetaInfo)
        }

        updatedPrompt shouldNotBe originalPrompt
        updatedPrompt.messages.size shouldBe 3
        updatedPrompt.messages[2] shouldBe Message.Assistant("How can I help you?", testRespMetaInfo)

        // Test replacing messages
        val replacedPrompt = originalPrompt.withMessages {
            listOf(Message.System("You are a coding assistant", testReqMetaInfo))
        }

        replacedPrompt.messages.size shouldBe 1
        replacedPrompt.messages[0] shouldBe Message.System("You are a coding assistant", testReqMetaInfo)
    }

    @Test
    fun testWithParamsFunction() {
        val originalPrompt = Prompt.build("test") {
            system("You are a helpful assistant")
        }

        val newParams = LLMParams(
            temperature = 0.7,
            speculation = "test speculation",
            user = "test_user",
        )

        val updatedPrompt = originalPrompt.withParams(newParams)

        updatedPrompt shouldNotBe originalPrompt
        updatedPrompt.params shouldBe newParams

        assertSoftly(updatedPrompt.params) {
            temperature shouldBe 0.7
            speculation shouldBe "test speculation"
            user shouldBe "test_user"
        }
    }

    @Test
    fun testWithUpdatedParamsFunction() {
        val originalPrompt = Prompt.build("test") {
            system("You are a helpful assistant")
        }

        // Test updating temperature only
        val tempUpdatedPrompt = originalPrompt.withUpdatedParams {
            temperature = 0.8
        }

        tempUpdatedPrompt shouldNotBe originalPrompt
        tempUpdatedPrompt.params.temperature shouldBe 0.8

        // Test updating multiple parameters
        val multiUpdatedPrompt = originalPrompt.withUpdatedParams {
            temperature = 0.5
            speculation = "new speculation"
            toolChoice = LLMParams.ToolChoice.Auto
            user = "new_user"
        }

        assertSoftly(multiUpdatedPrompt.params) {
            temperature shouldBe 0.5
            speculation shouldBe "new speculation"
            toolChoice shouldBe LLMParams.ToolChoice.Auto
            user shouldBe "new_user"
        }
    }
}
