package ai.koog.agents.core.feature.choice

import ai.koog.agents.core.feature.PromptExecutorChoice
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChoiceStrategyTest {

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    @Test
    @JsName("DummyChoiceStrategy_should_return_first_choice")
    fun `DummyChoiceStrategy should return first choice`() = runTest {
        // Arrange
        val strategy = DummyChoiceStrategy()
        val testPrompt = prompt("test") {}

        // Create two different choices
        val firstChoice: LLMChoice = listOf(Message.Assistant("First choice", metaInfo = ResponseMetaInfo.create(testClock)))
        val secondChoice: LLMChoice = listOf(Message.Assistant("Second choice", metaInfo = ResponseMetaInfo.create(testClock)))
        val choices = listOf(firstChoice, secondChoice)

        // Act
        val result = strategy.choose(testPrompt, choices)

        // Assert
        assertEquals(firstChoice, result, "DummyChoiceStrategy should return the first choice")
    }

    @Test
    @JsName("PromptExecutorChoice_should_delegate_to_strategy")
    fun `PromptExecutorChoice should delegate to strategy`() = runTest {
        // Arrange
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
                return listOf(Message.Assistant("Default response", metaInfo = ResponseMetaInfo.create(testClock)))
            }

            override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
                emit("Default streaming response")
            }

            override suspend fun executeMultipleChoices(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<LLMChoice> {
                val choice1 = listOf(Message.Assistant("Choice 1", metaInfo = ResponseMetaInfo.create(testClock)))
                val choice2 = listOf(Message.Assistant("Choice 2", metaInfo = ResponseMetaInfo.create(testClock)))
                return listOf(choice1, choice2)
            }
        }

        val mockStrategy = object : ChoiceStrategy {
            override suspend fun choose(prompt: Prompt, choices: List<LLMChoice>): LLMChoice {
                // Always choose the second choice
                return choices[1]
            }
        }

        val executor = PromptExecutorChoice(mockExecutor, mockStrategy)
        val testPrompt = prompt("test") {}
        val testModel = OllamaModels.Meta.LLAMA_3_2

        val result = executor.execute(testPrompt, testModel, emptyList())

        assertEquals("Choice 2", (result.first() as Message.Assistant).content, "PromptExecutorChoice should delegate to strategy and return the chosen choice")
    }

    @Test
    @JsName("AskUserChoiceStrategy_should_handle_valid_and_invalid_inputs")
    fun `AskUserChoiceStrategy should handle valid and invalid inputs`() = runTest {
        val printedOutput = mutableListOf<String>()
        val inputQueue = mutableListOf<String>()

        val strategy = AskUserChoiceStrategy(
            print = { message -> printedOutput.add(message) },
            read = { if (inputQueue.isNotEmpty()) inputQueue.removeAt(0) else "" }
        )

        val testPrompt = prompt("test") {}

        val choice1: LLMChoice = listOf(Message.Assistant("First choice", metaInfo = ResponseMetaInfo.create(testClock)))
        val choice2: LLMChoice = listOf(Message.Assistant("Second choice", metaInfo = ResponseMetaInfo.create(testClock)))
        val choice3: LLMChoice = listOf(Message.Assistant("Third choice", metaInfo = ResponseMetaInfo.create(testClock)))
        val choices = listOf(choice1, choice2, choice3)

        // Test case 1: Valid input "1" should return first choice
        inputQueue.add("1")
        var result = strategy.choose(testPrompt, choices)
        assertSame(choice1, result, "Strategy should return the first choice when user enters 1")
        printedOutput.clear()

        // Test case 2: Valid input "2" should return second choice
        inputQueue.add("2")
        result = strategy.choose(testPrompt, choices)
        assertSame(choice2, result, "Strategy should return the second choice when user enters 2")
        printedOutput.clear()

        // Test case 3: Valid input "3" should return third choice
        inputQueue.add("3")
        result = strategy.choose(testPrompt, choices)
        assertSame(choice3, result, "Strategy should return the third choice when user enters 3")
        printedOutput.clear()

        // Test case 4: Invalid input "0" followed by valid input "2" should return second choice
        inputQueue.add("0")
        inputQueue.add("2")
        result = strategy.choose(testPrompt, choices)
        assertSame(choice2, result, "Strategy should return the second choice when user enters 0 followed by 2")
        // Verify that "Invalid response" was printed
        assertTrue(printedOutput.any { it == "Invalid response." },
            "Strategy should print 'Invalid response.' when user enters invalid input")
        printedOutput.clear()

        // Test case 5: Invalid input "4" followed by valid input "3" should return third choice
        inputQueue.add("4")
        inputQueue.add("3")
        result = strategy.choose(testPrompt, choices)
        assertSame(choice3, result, "Strategy should return the third choice when user enters 4 followed by 3")
        // Verify that "Invalid response" was printed
        assertTrue(printedOutput.any { it == "Invalid response." }, 
            "Strategy should print 'Invalid response.' when user enters invalid input")
    }
}
