package ai.koog.prompt.executor.clients.bedrock

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock

internal actual class StaticCredentialsProvider actual constructor(
    @Suppress("UNUSED_PARAMETER") awsAccessKeyId: String,
    @Suppress("UNUSED_PARAMETER") awsSecretAccessKey: String
) {
    // JS specific: No actual credentials needed as it will be unsupported.
}

public actual fun createBedrockLLMClient(
    awsAccessKeyId: String,
    awsSecretAccessKey: String,
    settings: BedrockClientSettings,
    clock: Clock
): LLMClient = BedrockLLMClientJS()

private class BedrockLLMClientJS : LLMClient {
    private val unsupportedMessage = "AWS Bedrock client is not supported on Kotlin/JS"

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        console.warn(unsupportedMessage)
        throw UnsupportedOperationException(unsupportedMessage)
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        console.warn(unsupportedMessage)
        return flowOf()
    }
}
