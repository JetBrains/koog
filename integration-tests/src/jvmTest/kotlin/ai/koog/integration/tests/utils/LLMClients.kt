package ai.koog.integration.tests.utils

import ai.koog.integration.tests.utils.TestUtils.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSessionTokenFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenRouterKeyFromEnv
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.llm.LLMProvider
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider

/**
 * Common utility method to get correct [LLMClient] for a given [provider]
 */
fun getLLMClientForProvider(provider: LLMProvider): LLMClient {
    return when (provider) {
        LLMProvider.Anthropic -> AnthropicLLMClient(
            readTestAnthropicKeyFromEnv()
        )

        LLMProvider.OpenAI -> OpenAILLMClient(
            readTestOpenAIKeyFromEnv()
        )

        LLMProvider.OpenRouter -> OpenRouterLLMClient(
            readTestOpenRouterKeyFromEnv()
        )

        LLMProvider.Bedrock -> BedrockLLMClient(
            credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = readAwsAccessKeyIdFromEnv()
                this.secretAccessKey = readAwsSecretAccessKeyFromEnv()
                readAwsSessionTokenFromEnv()?.let { this.sessionToken = it }
            },
            settings = BedrockClientSettings()
        )

        LLMProvider.Google -> GoogleLLMClient(
            readTestGoogleAIKeyFromEnv()
        )

        else -> throw IllegalArgumentException("Unsupported provider: $provider")
    }
}
