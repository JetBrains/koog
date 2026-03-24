package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.cache.model.get
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.selection.ExperimentalSelectionApi
import ai.koog.prompt.executor.selection.ModelSelector
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

/**
 * A CodePromptExecutor that caches responses from a nested executor.
 *
 * @param cache The cache implementation to use
 * @param nested The nested executor to use for cache misses
 */
@OptIn(ExperimentalSelectionApi::class)
public class CachedPromptExecutor(
    private val cache: PromptCache,
    private val nested: PromptExecutor,
    private val clock: Clock = Clock.System
) : PromptExecutor() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = cache.get(prompt, tools, clock)
        ?: nested.execute(prompt, model, tools).also { cache.put(prompt, tools, it) }

    override suspend fun execute(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = getOrPut(prompt, tools, modelSelector)

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        (cache.get(prompt, tools, clock) ?: nested.execute(prompt, model, tools).also { cache.put(prompt, tools, it) })
            .toStreamFrames()
            .forEach { emit(it) }
    }

    override fun executeStreaming(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        getOrPut(prompt, tools, modelSelector)
            .toStreamFrames()
            .forEach { emit(it) }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = nested.executeMultipleChoices(prompt, model, tools)

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = nested.executeMultipleChoices(prompt, modelSelector, tools)

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        nested.moderate(prompt, model)

    override suspend fun moderate(prompt: Prompt, modelSelector: ModelSelector): ModerationResult =
        nested.moderate(prompt, modelSelector)

    override suspend fun models(): List<LLModel> = nested.models()

    override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator {
        return nested.getStandardJsonSchemaGenerator(model)
    }

    override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator {
        return nested.getBasicJsonSchemaGenerator(model)
    }

    override fun close() {
        nested.close()
    }

    private suspend fun getOrPut(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        modelSelector: ModelSelector
    ): List<Message.Response> {
        return cache.get(prompt, tools, clock)
            ?: nested.execute(prompt, modelSelector, tools).also { cache.put(prompt, tools, it) }
    }
}
