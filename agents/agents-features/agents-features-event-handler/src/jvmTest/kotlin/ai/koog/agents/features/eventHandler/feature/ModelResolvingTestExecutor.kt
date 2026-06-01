package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorOperation
import ai.koog.prompt.executor.model.ResolvedModel
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test [PromptExecutor] for test cases where model resolution is required.
 */
class ModelResolvingTestExecutor(
    private val clock: KoogClock,
    private val effectiveModel: LLModel,
) : PromptExecutor() {

    override suspend fun resolveModel(
        model: LLModel,
        promptExecutorOperation: PromptExecutorOperation,
    ): ResolvedModel = ResolvedModel(effectiveModel)

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant = Message.Assistant("Default test response", metaInfo = ResponseMetaInfo.create(clock))

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = flow {
        Message.Assistant("Default test response", metaInfo = ResponseMetaInfo.create(clock))
            .toStreamFrames()
            .forEach { emit(it) }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("Moderation is not used in this test")

    override fun close() {}
}
