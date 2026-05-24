@file:JvmMultifileClass
@file:JvmName("SimplePromptExecutors")

package ai.koog.prompt.executor.llms.all

import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.bedrock.StaticBearerTokenProvider
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * Creates an instance of `MultiLLMPromptExecutor` with a `BedrockLLMClient`.
 *
 * @param awsAccessKeyId Your AWS Access Key ID.
 * @param awsSecretAccessKey Your AWS Secret Access Key.
 * @param awsSessionToken Optional AWS session token for temporary security credentials (required if using temporary credentials, such as those from STS).
 * @param settings Custom client settings for region and timeouts.
 */
public fun simpleBedrockExecutor(
    awsAccessKeyId: String,
    awsSecretAccessKey: String,
    awsSessionToken: String? = null,
    settings: BedrockClientSettings = BedrockClientSettings()
): MultiLLMPromptExecutor =
    MultiLLMPromptExecutor(
        LLMProvider.Bedrock to BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                this.accessKeyId = awsAccessKeyId
                this.secretAccessKey = awsSecretAccessKey
                awsSessionToken?.let { this.sessionToken = it }
            },
            settings = settings,
        )
    )

/**
 * Creates an instance of `MultiLLMPromptExecutor` with a `BedrockLLMClient`.
 * Uses the provided Bedrock API key to create a [aws.smithy.kotlin.runtime.http.auth.BearerTokenProvider].
 *
 * See [AWS documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/api-keys-use.html) for more information
 * about Bedrock API keys.
 *
 * @param bedrockApiKey Your Bedrock API key (bearer token).
 * @param settings Custom client settings for region and timeouts.
 */
public fun simpleBedrockExecutorWithBearerToken(
    bedrockApiKey: String,
    settings: BedrockClientSettings = BedrockClientSettings()
): MultiLLMPromptExecutor =
    MultiLLMPromptExecutor(
        LLMProvider.Bedrock to BedrockLLMClient(
            identityProvider = StaticBearerTokenProvider(bedrockApiKey),
            settings = settings,
        )
    )
