package ai.koog.prompt.executor.selection

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelSelectionDslTest {

    // Given
    private val modelA = model(
        id = "a",
        capabilities = listOf(LLMCapability.Vision.Image, LLMCapability.ToolChoice),
        contextLength = 100_000,
        maxOutputTokens = 1_000,
    )
    private val modelB = model(
        id = "b",
        capabilities = listOf(LLMCapability.ToolChoice),
        contextLength = 200_000,
        maxOutputTokens = 10_000,
    )
    private val modelC = model(
        id = "c",
        capabilities = listOf(LLMCapability.Vision.Image, LLMCapability.ToolChoice),
        contextLength = 180_000,
        maxOutputTokens = 2_000,
    )
    private val testExecutor = TestSelectingPromptExecutor(modelA, modelB, modelC)
    private val testPrompt = prompt("test-selection-prompt") {
        assistant("Hello")
    }

    @Test
    fun testSelectionDslExecute() = runTest {
        // When
        testExecutor.execute(testPrompt) {
            withCapabilities(LLMCapability.Vision.Image, LLMCapability.ToolChoice)
            withMinContextLength(100_000)
            withMostOutputTokens()
        }

        // Then
        assertSelectedModels(modelC, modelA)
    }

    @Test
    fun testSelectionDslExecuteStreaming() = runTest {
        // When
        testExecutor.executeStreaming(testPrompt) {
            withBiggestContextLength()
        }.toList()

        // Then
        assertSelectedModels(modelB, modelC, modelA)
    }

    @Test
    fun testSelectionDslExecuteMultipleChoices() = runTest {
        // When
        testExecutor.executeMultipleChoices(testPrompt) {
            withCapabilities(LLMCapability.ToolChoice)
            withMinContextLength(200_000)
        }

        // Then
        assertSelectedModels(modelB)
    }

    @Test
    fun testSelectionDslModerate() = runTest {
        // When
        testExecutor.moderateWithSelection(testPrompt) {
            withCapabilities(LLMCapability.ToolChoice)
            withMinContextLength(150_000)
            withBiggestContextLength()
        }

        // Then
        assertSelectedModels(modelB, modelC)
    }

    @Test
    fun testBuilderRejectsNonPositiveConcurrency() {
        assertFailsWith<IllegalArgumentException> {
            ModelSelectorBuilder().withMaxConcurrentlyFilteredModels(0)
        }
    }

    private fun model(
        id: String,
        capabilities: List<LLMCapability>? = null,
        contextLength: Long? = null,
        maxOutputTokens: Long? = null,
    ): LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = id,
        capabilities = capabilities,
        contextLength = contextLength,
        maxOutputTokens = maxOutputTokens,
    )

    private fun assertSelectedModels(vararg model: LLModel) {
        assertEquals(listOf(*model), testExecutor.lastSelection.ranked)
    }
}
