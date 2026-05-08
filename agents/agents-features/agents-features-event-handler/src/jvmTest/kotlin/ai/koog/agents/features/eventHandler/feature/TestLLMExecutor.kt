package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.ExecutionCompleted
import ai.koog.prompt.executor.model.ExecutionDispatched
import ai.koog.prompt.executor.model.ExecutionFailed
import ai.koog.prompt.executor.model.ExecutionRequested
import ai.koog.prompt.executor.model.HookablePromptExecutor
import ai.koog.prompt.executor.model.MultipleChoicesCompleted
import ai.koog.prompt.executor.model.MultipleChoicesDispatched
import ai.koog.prompt.executor.model.MultipleChoicesFailed
import ai.koog.prompt.executor.model.MultipleChoicesRequested
import ai.koog.prompt.executor.model.PromptExecutionContext
import ai.koog.prompt.executor.model.StreamingCompleted
import ai.koog.prompt.executor.model.StreamingDispatched
import ai.koog.prompt.executor.model.StreamingFailed
import ai.koog.prompt.executor.model.StreamingFrameReceived
import ai.koog.prompt.executor.model.StreamingRequested
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TestLLMExecutor(val clock: KoogClock) : HookablePromptExecutor() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext,
    ): List<Message.Response> {
        context.handle(ExecutionRequested(context.promptExecutionId, prompt, model, tools))
        context.handle(ExecutionDispatched(context.promptExecutionId, prompt, model, tools))
        return try {
            val responses = listOf(handlePrompt(prompt))
            context.handle(ExecutionCompleted(context.promptExecutionId, prompt, model, tools, responses))
            responses
        } catch (e: Throwable) {
            context.handle(ExecutionFailed(context.promptExecutionId, prompt, model, tools, e))
            throw e
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext,
    ): Flow<StreamFrame> = flow {
        context.handle(StreamingRequested(context.promptExecutionId, prompt, model, tools))
        context.handle(StreamingDispatched(context.promptExecutionId, prompt, model, tools))
        try {
            handlePrompt(prompt).toStreamFrames().forEach { frame ->
                context.handle(StreamingFrameReceived(context.promptExecutionId, prompt, model, tools, frame))
                emit(frame)
            }
        } catch (e: Throwable) {
            context.handle(StreamingFailed(context.promptExecutionId, prompt, model, tools, e))
            throw e
        } finally {
            context.handle(StreamingCompleted(context.promptExecutionId, prompt, model, tools))
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext,
    ): List<LLMChoice> {
        context.handle(MultipleChoicesRequested(context.promptExecutionId, prompt, model, tools))
        context.handle(MultipleChoicesDispatched(context.promptExecutionId, prompt, model, tools))
        return try {
            val choices = listOf(listOf(handlePrompt(prompt)))
            context.handle(MultipleChoicesCompleted(context.promptExecutionId, prompt, model, tools, choices))
            choices
        } catch (e: Throwable) {
            context.handle(MultipleChoicesFailed(context.promptExecutionId, prompt, model, tools, e))
            throw e
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        context: PromptExecutionContext,
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not needed here")
    }

    private fun handlePrompt(prompt: Prompt): Message.Response {
        if (prompt.messages.any { it.content.contains("Summarize all the main achievements") }) {
            return Message.Assistant(
                "Here's a summary of the conversation: Test user asked questions and received responses.",
                metaInfo = ResponseMetaInfo.create(clock)
            )
        }

        return Message.Assistant("Default test response", metaInfo = ResponseMetaInfo.create(clock))
    }

    override fun close() {}
}
