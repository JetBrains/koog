package ai.koog.prompt.executor.selection

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.selection.PromptExecutorExtensions.execute
import ai.koog.prompt.executor.selection.PromptExecutorExtensions.executeMultipleChoices
import ai.koog.prompt.executor.selection.PromptExecutorExtensions.executeStreaming
import ai.koog.prompt.executor.selection.PromptExecutorExtensions.moderate
import ai.koog.prompt.executor.selection.PromptExecutorSelectionExtensionsTest.ExecutionSpec.Failure
import ai.koog.prompt.executor.selection.PromptExecutorSelectionExtensionsTest.ExecutionSpec.Success
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PromptExecutorSelectionExtensionsTest {

    @Test
    fun testExecuteWithTryBestAttemptsOnlyHighestRankedModel() = runTest {
        // Given
        val best = model(id = "best", maxOutputTokens = 2_000)
        val fallback = model(id = "fallback", maxOutputTokens = 1_000)
        val executor = RecordingPromptExecutor(
            fallback to Success,
            best to Success,
        )

        // When
        val response = executor.execute(prompt = Prompt.Empty) {
            withMostOutputTokens()
        }

        // Then
        assertEquals("ok:best", response.textContent())
        assertEquals(listOf(best), executor.attemptedModels)
    }

    @Test
    fun testExecuteWithTryUpToRetriesSecondModel() = runTest {
        // Given
        val best = model(id = "best", maxOutputTokens = 2_000)
        val fallback = model(id = "fallback", maxOutputTokens = 1_000)
        val executor = RecordingPromptExecutor(
            fallback to Success,
            best to Failure(IllegalStateException("best failed")),
        )

        // When
        val response = executor.execute(
            prompt = Prompt.Empty,
            selectionExecutionPolicy = TryUpTo(maxModelsToTry = 2)
        ) {
            withMostOutputTokens()
        }

        // Then
        assertEquals("ok:fallback", response.textContent())
        assertEquals(listOf(best, fallback), executor.attemptedModels)
    }

    @Test
    fun testExecuteWithTryAllThrowsAfterAllModelsFail() = runTest {
        // Given
        val first = model(id = "first", maxOutputTokens = 2_000)
        val second = model(id = "second", maxOutputTokens = 1_000)
        val executor = RecordingPromptExecutor(
            first to Failure(IllegalStateException("first failed")),
            second to Failure(IllegalStateException("second failed")),
        )

        // When, Then
        assertFailsWith<SelectionExecutionException> {
            executor.execute(
                prompt = Prompt.Empty,
                selectionExecutionPolicy = TryAll
            ) {
                withMostOutputTokens()
            }
        }
        assertEquals(listOf(first, second), executor.attemptedModels)
    }

    @Test
    fun testExecuteMultipleChoicesSelectsExpectedModel() = runTest {
        // Given
        val selected = model(id = "selected")
        val skipped = model(id = "skipped")
        val executor = RecordingPromptExecutor(
            skipped to Success,
            selected to Success,
        )

        // When
        val choices = executor.executeMultipleChoices(prompt = Prompt.Empty) {
            withFilter { it.id == "selected" }
        }

        // Then
        assertEquals(listOf("ok:selected"), choices.map { it.textContent() })
        assertEquals(listOf(selected), executor.attemptedModels)
    }

    @Test
    fun testModerateSelectsExpectedModel() = runTest {
        // Given
        val selected = model(id = "selected")
        val skipped = model(id = "skipped")
        val executor = RecordingPromptExecutor(
            skipped to Success,
            selected to Success,
        )

        // When
        val result = executor.moderate(prompt = Prompt.Empty) {
            withFilter { it.id == "selected" }
        }

        // Then
        assertEquals(ModerationResult(isHarmful = false, categories = emptyMap()), result)
        assertEquals(listOf(selected), executor.attemptedModels)
    }

    @Test
    fun testExecuteStreamingRetriesFailureBeforeFirstFrame() = runTest {
        // Given
        val best = model(id = "best", maxOutputTokens = 2_000)
        val fallback = model(id = "fallback", maxOutputTokens = 1_000)
        val executor = RecordingPromptExecutor(
            fallback to Success,
            best to Failure(IllegalStateException("best failed")),
        )

        // When
        val frames = executor.executeStreaming(
            prompt = Prompt.Empty,
            selectionExecutionPolicy = TryUpTo(maxModelsToTry = 2)
        ) {
            withMostOutputTokens()
        }.toList()

        // Then
        assertEquals(listOf(StreamFrame.TextDelta("ok:fallback")), frames)
        assertEquals(listOf(best, fallback), executor.attemptedModels)
    }

    private class RecordingPromptExecutor(
        private val modelsSpecs: Map<LLModel, ExecutionSpec>
    ) : PromptExecutor() {

        constructor(vararg modelsSpecs: Pair<LLModel, ExecutionSpec>) : this(modelsSpecs.toMap())

        val attemptedModels: MutableList<LLModel> = mutableListOf()

        override suspend fun models(): List<LLModel> = modelsSpecs.keys.toList()

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Message.Assistant {
            record(model)
            return response(model)
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Flow<StreamFrame> = flow {
            record(model)
            emit(StreamFrame.TextDelta("ok:${model.id}"))
        }

        override suspend fun executeMultipleChoices(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): LLMChoice {
            record(model)
            return listOf(response(model))
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            record(model)
            return ModerationResult(isHarmful = false, categories = emptyMap())
        }

        override fun close() = Unit

        private fun record(model: LLModel) {
            attemptedModels += model
            when (val spec = modelsSpecs.getValue(model)) {
                is Failure -> throw spec.e
                Success -> Unit
            }
        }

        private fun response(model: LLModel): Message.Assistant =
            Message.Assistant("ok:${model.id}", ResponseMetaInfo.Empty)
    }

    private sealed class ExecutionSpec {
        data object Success : ExecutionSpec()
        data class Failure(val e: Exception) : ExecutionSpec()
    }

    private fun model(
        id: String,
        maxOutputTokens: Long? = null,
    ): LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = id,
        maxOutputTokens = maxOutputTokens,
    )
}
