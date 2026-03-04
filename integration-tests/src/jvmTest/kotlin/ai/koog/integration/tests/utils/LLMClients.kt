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
import ai.koog.prompt.executor.clients.bedrock.BedrockAPIMethod
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
 * Returns null if required credentials are not available
 */
fun getLLMClientForProvider(provider: LLMProvider): LLMClient? {
    return when (provider) {
        LLMProvider.Anthropic -> {
            val apiKey = readTestAnthropicKeyFromEnv() ?: return null
            AnthropicLLMClient(apiKey)
        }

        LLMProvider.OpenAI -> {
            val apiKey = readTestOpenAIKeyFromEnv() ?: return null
            OpenAILLMClient(apiKey)
        }

        LLMProvider.OpenRouter -> {
            val apiKey = readTestOpenRouterKeyFromEnv() ?: return null
            OpenRouterLLMClient(apiKey)
        }

        LLMProvider.Bedrock -> {
            val accessKeyId = readAwsAccessKeyIdFromEnv() ?: return null
            val secretAccessKey = readAwsSecretAccessKeyFromEnv() ?: return null
            val guardrailId = readAwsBedrockGuardrailIdFromEnv() ?: return null
            val guardrailVersion = readAwsBedrockGuardrailVersionFromEnv() ?: return null

            BedrockLLMClient(
                identityProvider = StaticCredentialsProvider {
                    this.accessKeyId = accessKeyId
                    this.secretAccessKey = secretAccessKey
                    readAwsSessionTokenFromEnv()?.let { this.sessionToken = it }
                },
                settings = BedrockClientSettings(
                    moderationGuardrailsSettings = BedrockGuardrailsSettings(
                        guardrailIdentifier = guardrailId,
                        guardrailVersion = guardrailVersion
                    ),
                    apiMethod = BedrockAPIMethod.InvokeModel,
                )
            )
        }

        LLMProvider.Google -> {
            val apiKey = readTestGoogleAIKeyFromEnv() ?: return null
            GoogleLLMClient(apiKey)
        }

        LLMProvider.MistralAI -> {
            val apiKey = readTestMistralAiKeyFromEnv() ?: return null
            MistralAILLMClient(apiKey)
        }

        else -> throw IllegalArgumentException("Unsupported provider: $provider")
    }
}

/**
 * Gets [LLMClient] for a given [provider] or throws [org.opentest4j.TestAbortedException] if credentials are not available
 */
fun getLLMClientForProviderOrSkip(provider: LLMProvider): LLMClient {
    return getLLMClientForProvider(provider)
        ?: throw org.opentest4j.TestAbortedException("Credentials for $provider are not available")
}
