package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlin.jvm.JvmOverloads

internal class MockOpenAILLMClient @JvmOverloads constructor(
    private val executeResponseContent: String = "OpenAI response",
    private val throwException: Boolean = false,
    private val clock: Clock = Clock.System,
) : LLMClient {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        if (throwException) {
            error("Throw exception for test")
        } else {
            return listOf(
                Message.Assistant(
                    executeResponseContent,
                    ResponseMetaInfo.create(clock)
                )
            )
        }
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        return flowOf("OpenAI", " streaming", " response")
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        throw UnsupportedOperationException("Moderation is not supported by mock client.")
    }
}
