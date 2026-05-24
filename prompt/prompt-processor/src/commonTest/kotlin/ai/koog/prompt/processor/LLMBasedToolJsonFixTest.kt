package ai.koog.prompt.processor

import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LLMBasedToolJsonFixTest {
    private companion object {
        private val testClock: KoogClock = KoogClock { Instant.parse("2023-01-01T00:00:00Z") }

        private val testMetaInfo = ResponseMetaInfo.create(testClock)

        private val toolCallMessage = MessagePart.Tool.Call(
            id = null,
            tool = "plus",
            args = """{"a":5.0,"b":3.0}""",
        )

        private val toolRegistry = Tools.toolRegistry

        private val tools = toolRegistry.tools.map { it.descriptor }
        private val prompt = prompt("test-prompt") { }
        private val model = OpenAIModels.Chat.GPT4o

        private val message = Message.Assistant("I want to use the calculator tool", metaInfo = testMetaInfo)

        val processor = LLMBasedToolCallFixProcessor(toolRegistry)
    }

    private val serializer = KotlinxSerializer()

    private fun testAssistantMessage(parts: List<MessagePart.ResponsePart>) =
        Message.Assistant(parts = parts, metaInfo = testMetaInfo)

    private suspend fun process(
        executor: PromptExecutor,
        response: Message.Assistant,
        processor: ResponseProcessor
    ) = processor.process(executor, prompt, model, tools, response, serializer)

    @Test
    fun test_shouldStopIfToolCallNotIntended() = runTest {
        val executor = getMockExecutor(serializer, testClock) {
            mockLLMAnswer(Prompts.NOT_INTENDED_TOOL_CALL) onRequestContains "I want to use the calculator tool"
        }
        val result = process(executor, message, processor)

        assertEquals(message, result)
    }

    @Test
    fun test_shouldFixAssistantMessage() = runTest {
        val executor = getMockExecutor(serializer, testClock) {
            mockLLMAnswer(Prompts.INTENDED_TOOL_CALL) onRequestContains "I want to use the calculator tool"
            mockLLMToolCall(Tools.PlusTool, Tools.PlusTool.Args(5f, 3f)) onRequestContains "not in the proper tool call format"
        }
        val result = process(executor, message, processor)

        assertEquals(testAssistantMessage(listOf(toolCallMessage)), result)
    }

    @Test
    fun test_shouldFixInvalidToolName() = runTest {
        val executor = getMockExecutor(serializer, testClock) {
            mockLLMToolCall(Tools.PlusTool, Tools.PlusTool.Args(5f, 3f)) onRequestContains """Tool name "minus" is not recognized"""
        }
        val message = testAssistantMessage(listOf(toolCallMessage.copy(tool = "minus")))
        val result = process(executor, message, processor)

        assertEquals(testAssistantMessage(listOf(toolCallMessage)), result)
    }

    @Test
    fun test_shouldFixIncorrectArguments() = runTest {
        val executor = getMockExecutor(serializer, testClock) {
            mockLLMToolCall(Tools.PlusTool, Tools.PlusTool.Args(5f, 3f)) onRequestContains "Failed to parse tool arguments with error"
        }
        val message = testAssistantMessage(
            listOf(
                MessagePart.Tool.Call(
                    id = null,
                    tool = "plus",
                    args = """{"x":5,"y":3}""",
                )
            )
        )
        val result = process(executor, message, processor)

        assertEquals(testAssistantMessage(listOf(toolCallMessage)), result)
    }

    @Test
    fun test_shouldRetry() = runTest {
        val executor = getMockExecutor(serializer, testClock) {
            mockLLMAnswer(Prompts.INTENDED_TOOL_CALL) onRequestContains "I want to use the calculator tool"
            // First fix attempt returns text with extractable tool name "minus" → triggers invalidNameFeedback on next iteration
            mockLLMAnswer("""I intended to call "tool": "minus" but the format was wrong""") onRequestContains "not in the proper tool call format"
            // Second fix attempt: correct tool call
            mockLLMToolCall(Tools.PlusTool, Tools.PlusTool.Args(5f, 3f)) onRequestContains """Tool name "minus" is not recognized"""
        }
        val result = process(executor, message, processor)

        assertEquals(testAssistantMessage(listOf(toolCallMessage)), result)
    }

    @Test
    fun test_shouldStopWhenMaxRetriesReached() = runTest {
        val someText = "I cannot fix this tool call"
        val executor = getMockExecutor(serializer, testClock) {
            mockLLMAnswer(Prompts.INTENDED_TOOL_CALL) onRequestContains "I want to use the calculator tool"
            mockLLMAnswer(someText).asDefaultResponse
        }
        val processor = LLMBasedToolCallFixProcessor(
            toolRegistry = toolRegistry,
            maxRetries = 1,
        )
        val result = process(executor, message, processor)

        assertEquals(testAssistantMessage(listOf(MessagePart.Text(someText))), result)
    }
}
