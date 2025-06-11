package ai.koog.agents.core.feature.reply.choice

import ai.koog.agents.core.feature.PromptExecutorReplyChoice
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.LLMReply
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReplyChoiceStrategyTest {

    private val testClock: Clock = object : Clock {
        override fun now(): kotlinx.datetime.Instant = kotlinx.datetime.Instant.parse("2023-01-01T00:00:00Z")
    }

    @Test
    @JsName("DummyReplyChoiceStrategy_should_return_first_reply")
    fun `DummyReplyChoiceStrategy should return first reply`() = runTest {
        // Arrange
        val strategy = DummyReplyChoiceStrategy()
        val testPrompt = prompt("test") {}

        // Create two different replies
        val firstReply: LLMReply = listOf(Message.Assistant("First reply", metaInfo = ResponseMetaInfo.create(testClock)))
        val secondReply: LLMReply = listOf(Message.Assistant("Second reply", metaInfo = ResponseMetaInfo.create(testClock)))
        val replies = listOf(firstReply, secondReply)

        // Act
        val result = strategy.chooseReply(testPrompt, replies)

        // Assert
        assertEquals(firstReply, result, "DummyReplyChoiceStrategy should return the first reply")
    }

    @Test
    @JsName("PromptExecutorReplyChoice_should_delegate_to_strategy")
    fun `PromptExecutorReplyChoice should delegate to strategy`() = runTest {
        // Arrange
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
                return listOf(Message.Assistant("Default response", metaInfo = ResponseMetaInfo.create(testClock)))
            }

            override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
                emit("Default streaming response")
            }

            override suspend fun executeMultipleReplies(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<LLMReply> {
                val reply1 = listOf(Message.Assistant("Reply 1", metaInfo = ResponseMetaInfo.create(testClock)))
                val reply2 = listOf(Message.Assistant("Reply 2", metaInfo = ResponseMetaInfo.create(testClock)))
                return listOf(reply1, reply2)
            }
        }

        val mockStrategy = object : ReplyChoiceStrategy {
            override suspend fun chooseReply(prompt: Prompt, replies: List<LLMReply>): LLMReply {
                // Always choose the second reply
                return replies[1]
            }
        }

        val executor = PromptExecutorReplyChoice(mockExecutor, mockStrategy)
        val testPrompt = prompt("test") {}
        val testModel = OllamaModels.Meta.LLAMA_3_2

        val result = executor.execute(testPrompt, testModel, emptyList())

        assertEquals("Reply 2", (result.first() as Message.Assistant).content, "PromptExecutorReplyChoice should delegate to strategy and return the chosen reply")
    }

    @Test
    @JsName("AskUserReplyChoiceStrategy_should_handle_valid_and_invalid_inputs")
    fun `AskUserReplyChoiceStrategy should handle valid and invalid inputs`() = runTest {
        val printedOutput = mutableListOf<String>()
        val inputQueue = mutableListOf<String>()

        val strategy = AskUserReplyChoiceStrategy(
            print = { message -> printedOutput.add(message) },
            read = { if (inputQueue.isNotEmpty()) inputQueue.removeAt(0) else "" }
        )

        val testPrompt = prompt("test") {}

        val reply1: LLMReply = listOf(Message.Assistant("First reply", metaInfo = ResponseMetaInfo.create(testClock)))
        val reply2: LLMReply = listOf(Message.Assistant("Second reply", metaInfo = ResponseMetaInfo.create(testClock)))
        val reply3: LLMReply = listOf(Message.Assistant("Third reply", metaInfo = ResponseMetaInfo.create(testClock)))
        val replies = listOf(reply1, reply2, reply3)

        // Test case 1: Valid input "1" should return first reply
        inputQueue.add("1")
        var result = strategy.chooseReply(testPrompt, replies)
        assertSame(reply1, result, "Strategy should return the first reply when user enters 1")
        printedOutput.clear()

        // Test case 2: Valid input "2" should return second reply
        inputQueue.add("2")
        result = strategy.chooseReply(testPrompt, replies)
        assertSame(reply2, result, "Strategy should return the second reply when user enters 2")
        printedOutput.clear()

        // Test case 3: Valid input "3" should return third reply
        inputQueue.add("3")
        result = strategy.chooseReply(testPrompt, replies)
        assertSame(reply3, result, "Strategy should return the third reply when user enters 3")
        printedOutput.clear()

        // Test case 4: Invalid input "0" followed by valid input "2" should return second reply
        inputQueue.add("0")
        inputQueue.add("2")
        result = strategy.chooseReply(testPrompt, replies)
        assertSame(reply2, result, "Strategy should return the second reply when user enters 0 followed by 2")
        // Verify that "Invalid response" was printed
        assertTrue(printedOutput.any { it == "Invalid response." }, 
            "Strategy should print 'Invalid response.' when user enters invalid input")
        printedOutput.clear()

        // Test case 5: Invalid input "4" followed by valid input "3" should return third reply
        inputQueue.add("4")
        inputQueue.add("3")
        result = strategy.chooseReply(testPrompt, replies)
        assertSame(reply3, result, "Strategy should return the third reply when user enters 4 followed by 3")
        // Verify that "Invalid response" was printed
        assertTrue(printedOutput.any { it == "Invalid response." }, 
            "Strategy should print 'Invalid response.' when user enters invalid input")
    }
}
