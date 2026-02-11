package ai.koog.ktor

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockGuardrailsSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.bedrock.StaticBearerTokenProvider
import ai.koog.prompt.llm.LLMProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import kotlinx.datetime.Clock

/**
 * Configuration to create a new Bedrock LLM client configured with the specified identity provider and settings.
 */
public class BedrockClientConfig {

    /**
     * Configure the underlying BedrockRuntimeClient from the AWS SDK.
     */
    public var configure: BedrockRuntimeClient.Config.Builder.() -> Unit = {}

    /**
     * Set moderation guardrails settings for Bedrock.
     */
    public var moderationGuardrailsSettings: BedrockGuardrailsSettings? = null

    /**
     * Override the clock used for time-based operations.
     */
    public var clock: Clock = Clock.System
}

/**
 * Configures and initializes a Bedrock LLM client with optional configuration.
 *
 * @param apiKey The API key used for authenticating with the Bedrock API.
 * @param clock A clock used for time-based operations
 * @param moderationGuardrailsSettings Optional settings of the AWS bedrock Guardrails (see [AWS documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-independent-api.html) ) that would be used for the [LLMClient.moderate] request
 * @param configure A lambda receiver to customize the OpenAI configuration such as base URL, timeout settings, and paths.
 */
public fun KoogAgentsConfig.bedrock(
    apiKey: String,
    clock: Clock = Clock.System,
    moderationGuardrailsSettings: BedrockGuardrailsSettings? = null,
    configure: BedrockRuntimeClient.Config.Builder.() -> Unit = {}
) {
    val client = BedrockRuntimeClient {
        configure()
        bearerTokenProvider = StaticBearerTokenProvider(apiKey)
    }
    addLLMClient(
        LLMProvider.Bedrock,
        BedrockLLMClient(
            bedrockClient = client,
            moderationGuardrailsSettings = moderationGuardrailsSettings,
            clock = clock
        )
    )
}

/**
 * Configures and initializes a Bedrock LLM client with AWS SDK builder
 *
 * @param clock A clock used for time-based operations
 * @param moderationGuardrailsSettings Optional settings of the AWS bedrock Guardrails (see [AWS documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-independent-api.html) ) that would be used for the [LLMClient.moderate] request
 * @param configure A lambda receiver to customize the OpenAI configuration such as base URL, timeout settings, and paths.
 */
public fun KoogAgentsConfig.bedrock(
    clock: Clock = Clock.System,
    moderationGuardrailsSettings: BedrockGuardrailsSettings? = null,
    configure: BedrockRuntimeClient.Config.Builder.() -> Unit
) {
    val client = BedrockRuntimeClient {
        configure()
    }
    addLLMClient(
        LLMProvider.Bedrock,
        BedrockLLMClient(
            bedrockClient = client,
            moderationGuardrailsSettings = moderationGuardrailsSettings,
            clock = clock
        )
    )
}
