@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalPromptAPI::class)

package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.annotations.InternalPromptAPI
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.coroutines.flow.Flow

public actual class PromptExecutor internal actual constructor(
    @property:InternalPromptAPI public actual val builder: PromptExecutorBuilder,
) : PromptExecutorAPI {

    public actual fun resolveModel(model: LLModel, operation: PromptExecutorOperation): LLModel =
        builder.resolveModel(model, operation)

    actual override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant =
        builder.onExecute(prompt, builder.resolveModel(model, PromptExecutorOperation.Execute), tools)

    actual override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> =
        builder.onStreaming(prompt, builder.resolveModel(model, PromptExecutorOperation.Streaming), tools)

    actual override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): LLMChoice =
        builder.onMultipleChoices(prompt, builder.resolveModel(model, PromptExecutorOperation.MultipleChoices), tools)

    actual override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        builder.onModerate(prompt, builder.resolveModel(model, PromptExecutorOperation.Moderate))

    actual override suspend fun models(): List<LLModel> = builder.onModels()

    actual override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator =
        builder.getStandardJsonSchemaGenerator(model)

    actual override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator =
        builder.getBasicJsonSchemaGenerator(model)

    actual override fun close() {
        builder.onClose()
    }
}
