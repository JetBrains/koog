package ai.koog.integration.tests.utils

import ai.koog.integration.tests.utils.APIKeys.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.APIKeys.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.APIKeys.readAwsSessionTokenFromEnv
import ai.koog.integration.tests.utils.APIKeys.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.APIKeys.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.APIKeys.readTestMistralAiKeyFromEnv
import ai.koog.integration.tests.utils.APIKeys.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.APIKeys.readTestOpenRouterKeyFromEnv
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
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
            identityProvider = StaticCredentialsProvider {
                this.accessKeyId = readAwsAccessKeyIdFromEnv()
                this.secretAccessKey = readAwsSecretAccessKeyFromEnv()
                readAwsSessionTokenFromEnv()?.let { this.sessionToken = it }
            },
            settings = BedrockClientSettings()
        )

        LLMProvider.Google -> GoogleLLMClient(
            readTestGoogleAIKeyFromEnv()
        )

        LLMProvider.MistralAI -> MistralAILLMClient(
            readTestMistralAiKeyFromEnv()
        )

        else -> throw IllegalArgumentException("Unsupported provider: $provider")
    }
}
