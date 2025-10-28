package ai.koog.agents.ext.llm.choice

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.streamFrameFlowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals

class ChoiceSelectionStrategyTest {

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    @Test
    @JsName("DummyChoiceStrategy_should_return_first_choice")
    fun `Default should return first choice`() = runTest {
        // Arrange
        val choiceSelectionStrategy = ChoiceSelectionStrategy.Default
        val testPrompt = prompt("test") {}

        // Create two different choices
        val firstChoice: LLMChoice =
            listOf(Message.Assistant("First choice", metaInfo = ResponseMetaInfo.create(testClock)))
        val secondChoice: LLMChoice =
            listOf(Message.Assistant("Second choice", metaInfo = ResponseMetaInfo.create(testClock)))
        val choices = listOf(firstChoice, secondChoice)

        // Act
        val result = choiceSelectionStrategy.choose(testPrompt, choices)

        // Assert
        assertEquals(firstChoice, result, "DummyChoiceStrategy should return the first choice")
    }

    @Test
    @JsName("PromptExecutorChoice_should_delegate_to_strategy")
    fun `PromptExecutorWithChoiceSelection should delegate to strategy`() = runTest {
        // Arrange
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> {
                return listOf(
                    Message.Assistant(
                        "Default response",
                        metaInfo = ResponseMetaInfo.create(testClock)
                    )
                )
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> =
                streamFrameFlowOf("Default streaming response")

            override suspend fun executeMultipleChoices(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<LLMChoice> {
                val choice1 =
                    listOf(Message.Assistant("Choice 1", metaInfo = ResponseMetaInfo.create(testClock)))
                val choice2 =
                    listOf(Message.Assistant("Choice 2", metaInfo = ResponseMetaInfo.create(testClock)))
                return listOf(choice1, choice2)
            }

            override suspend fun moderate(
                prompt: Prompt,
                model: LLModel
            ): ModerationResult {
                throw UnsupportedOperationException("Moderation is not needed here")
            }

            override fun close() {}
        }

        val mockStrategy = object : ChoiceSelectionStrategy {
            override suspend fun choose(prompt: Prompt, choices: List<LLMChoice>): LLMChoice {
                // Always choose the second choice
                return choices[1]
            }
        }

        val executor = PromptExecutorWithChoiceSelection(mockExecutor, mockStrategy)
        val testPrompt = prompt("test") {}
        val testModel = OllamaModels.Meta.LLAMA_3_2

        val result = executor.execute(testPrompt, testModel, emptyList())

        assertEquals(
            "Choice 2",
            (result.first() as Message.Assistant).content,
            "PromptExecutorChoice should delegate to strategy and return the chosen choice"
        )
    }
}
