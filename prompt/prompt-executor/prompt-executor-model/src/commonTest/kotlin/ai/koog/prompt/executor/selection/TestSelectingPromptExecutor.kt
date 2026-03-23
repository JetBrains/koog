package ai.koog.prompt.executor.selection

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

internal class TestSelectingPromptExecutor(
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
