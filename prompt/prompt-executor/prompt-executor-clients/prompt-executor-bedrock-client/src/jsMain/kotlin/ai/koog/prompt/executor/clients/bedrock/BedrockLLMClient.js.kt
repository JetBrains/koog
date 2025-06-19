package ai.koog.prompt.executor.clients.bedrock

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

internal actual class StaticCredentialsProvider actual constructor(
    @Suppress("UNUSED_PARAMETER") awsAccessKeyId: String,
    @Suppress("UNUSED_PARAMETER") awsSecretAccessKey: String
) {
    // JS specific: No actual credentials needed as it will be unsupported.
}

/**
 * Creates a no-op Bedrock LLM client for Kotlin/JS. This function is included for interface
 * compatibility but will not establish a real AWS Bedrock connection. The provided credentials and 
 * settings are unused in the JS implementation as AWS SDK is not supported here.
 *
 * @param awsAccessKeyId AWS access key ID (ignored on JS)
 * @param awsSecretAccessKey AWS secret access key (ignored on JS)
 * @param settings Configuration settings for the client (ignored on JS)
 * @param clock Clock instance for time-based operations (ignored on JS)
 * @return A [LLMClient] that throws [UnsupportedOperationException] on all operations
 */
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
        throw UnsupportedOperationException(unsupportedMessage)
    }
}
