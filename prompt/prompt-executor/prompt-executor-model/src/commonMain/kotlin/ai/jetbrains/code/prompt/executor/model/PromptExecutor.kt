package ai.jetbrains.code.prompt.executor.model

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow

interface PromptExecutor {
    suspend fun execute(prompt: Prompt): String = execute(prompt, emptyList()).first().content
    suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response>
    suspend fun executeStreaming(prompt: Prompt): Flow<String>
}