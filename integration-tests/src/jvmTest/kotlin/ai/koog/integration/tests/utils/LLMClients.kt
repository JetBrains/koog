package ai.koog.integration.tests.utils

import ai.koog.integration.tests.utils.TestCredentials.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsBedrockGuardrailIdFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsBedrockGuardrailVersionFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsSessionTokenFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readTestMistralAiKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readTestOpenRouterKeyFromEnv
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockGuardrailsSettings
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
            settings = BedrockClientSettings(
                moderationGuardrailsSettings = BedrockGuardrailsSettings(
                    guardrailIdentifier = readAwsBedrockGuardrailIdFromEnv(),
                    guardrailVersion = readAwsBedrockGuardrailVersionFromEnv()
                )
            )
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
