package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow

public abstract class HookablePromptExecutor : PromptExecutor() {

    public abstract suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: ExecuteHook?
    ): List<Message.Response>

    public abstract fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: StreamingHook?
    ): Flow<StreamFrame>

    public abstract suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: MultipleChoicesHook?
    ): List<LLMChoice>

    public abstract suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        hook: ModerateHook?
    ): ModerationResult

    // PromptExecutorAPI delegation — hooks-free callers go through here

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> = execute(prompt, model, tools, null)

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = executeStreaming(prompt, model, tools, null)

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<LLMChoice> = executeMultipleChoices(prompt, model, tools, null)

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = moderate(prompt, model, null)
}
