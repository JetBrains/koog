package ai.koog.agents.ext.llm.choice

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.testing.client.CapturingLLMClient
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.ExecuteHook
import ai.koog.prompt.executor.model.ExecutionArgOverrides
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.ModerationHook
import ai.koog.prompt.executor.model.MultipleChoicesHook
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.ResolvedExecutionIntent
import ai.koog.prompt.executor.model.StreamingExecutorHook
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.streamFrameFlowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

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
        val mockExecutor = object : PromptExecutor() {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>,
                hook: ExecuteHook?
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
                tools: List<ToolDescriptor>,
                hook: StreamingExecutorHook?
            ): Flow<StreamFrame> =
                streamFrameFlowOf("Default streaming response")

            override suspend fun executeMultipleChoices(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>,
                hook: MultipleChoicesHook?
            ): List<LLMChoice> {
                val choice1 =
                    listOf(Message.Assistant("Choice 1", metaInfo = ResponseMetaInfo.create(testClock)))
                val choice2 =
                    listOf(Message.Assistant("Choice 2", metaInfo = ResponseMetaInfo.create(testClock)))
                return listOf(choice1, choice2)
            }

            override suspend fun moderate(prompt: Prompt, model: LLModel, hook: ModerationHook?): ModerationResult {
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

    @Test
    fun testExecuteHookOnCompletedReceivesSelectedChoice() = runTest {
        val choice1 = listOf(Message.Assistant("Choice 1", metaInfo = ResponseMetaInfo.create(testClock)))
        val choice2 = listOf(Message.Assistant("Choice 2", metaInfo = ResponseMetaInfo.create(testClock)))

        val client = CapturingLLMClient(choices = listOf(choice1, choice2))
        val executor = PromptExecutorWithChoiceSelection(SingleLLMPromptExecutor(client), ChoiceSelectionStrategy.Default)

        var completedWith: List<Message.Response>? = null
        val hook = object : ExecuteHook {
            override suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel, result: List<Message.Response>) {
                completedWith = result
            }
        }

        val result = executor.execute(prompt("test") {}, OllamaModels.Meta.LLAMA_3_2, emptyList(), hook)

        assertEquals(choice1, result)
        assertEquals(choice1, completedWith, "onCompleted should receive the selected choice, not all choices")
    }

    @Test
    fun testExecuteHookBeforeExecutionPromptOverrideIsForwarded() = runTest {
        val overriddenPrompt = prompt("overridden") { user("overridden") }
        val client = CapturingLLMClient(choices = listOf(listOf(Message.Assistant("ok", ResponseMetaInfo.create(testClock)))))
        val executor = PromptExecutorWithChoiceSelection(SingleLLMPromptExecutor(client), ChoiceSelectionStrategy.Default)

        val hook = object : ExecuteHook {
            override suspend fun beforeExecution(intent: InitialExecutionIntent, effectiveModel: LLModel) =
                ExecutionArgOverrides.UseDifferentPrompt(overriddenPrompt)
        }

        executor.execute(prompt("original") {}, OllamaModels.Meta.LLAMA_3_2, emptyList(), hook)

        assertEquals(overriddenPrompt, client.lastChoicesPrompt, "beforeExecution prompt override should reach the inner executor")
    }
}
