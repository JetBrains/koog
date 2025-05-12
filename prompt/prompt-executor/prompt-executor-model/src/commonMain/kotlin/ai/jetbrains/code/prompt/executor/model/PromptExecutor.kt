package ai.jetbrains.code.prompt.executor.model

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow

public interface PromptExecutor {
    public suspend fun execute(prompt: Prompt, model: LLModel): String = execute(prompt, model, emptyList()).first().content
    public suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response>
    public suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String>
}
