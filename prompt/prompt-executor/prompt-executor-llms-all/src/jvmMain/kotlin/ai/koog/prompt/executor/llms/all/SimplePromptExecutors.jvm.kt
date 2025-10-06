package ai.koog.prompt.executor.llms.all

import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider

/**
 * Creates an instance of `SingleLLMPromptExecutor` with a `BedrockLLMClient`.
 *
 * @param awsAccessKeyId Your AWS Access Key ID.
 * @param awsSecretAccessKey Your AWS Secret Access Key.
 * @param settings Custom client settings for region and timeouts.
 */
public fun simpleBedrockExecutor(
    awsAccessKeyId: String,
    awsSecretAccessKey: String,
    awsSessionToken: String? = null,
    settings: BedrockClientSettings = BedrockClientSettings()
): SingleLLMPromptExecutor =
    SingleLLMPromptExecutor(
        BedrockLLMClient(
            credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = awsAccessKeyId
                this.secretAccessKey = awsSecretAccessKey
                awsSessionToken?.let { this.sessionToken = it }
            },
            settings = settings,
        )
    )
