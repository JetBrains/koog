package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
            ModelSelectionBuilder().withMaxConcurrentlyFilteredModels(0)
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

private class TestSelectingPromptExecutor(
    private val availableModels: List<LLModel>,
) : SelectingPromptExecutor() {

    constructor(vararg models: LLModel) : this(models.toList())

    var lastSelection: ModelSelection = ModelSelection.EMPTY
        private set

    override suspend fun execute(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        val selection = modelSelector.select(availableModels)
        lastSelection = selection
        return listOf(Message.Assistant(content = "Irrelevant for test", metaInfo = ResponseMetaInfo.Empty))
    }

    override fun executeStreaming(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> {
        return flow {
            lastSelection = modelSelector.select(availableModels)
            emitAll(emptyFlow())
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>,
    ): List<LLMChoice> {
        lastSelection = modelSelector.select(availableModels)
        return listOf(
            listOf(Message.Assistant(content = "Irrelevant for test", metaInfo = ResponseMetaInfo.Empty))
        )
    }

    override suspend fun moderate(prompt: Prompt, modelSelector: ModelSelector): ModerationResult {
        lastSelection = modelSelector.select(availableModels)
        return ModerationResult(isHarmful = false, categories = emptyMap())
    }

    override suspend fun models(): List<LLModel> = availableModels

    override fun close() = Unit
}
