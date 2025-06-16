package ai.koog.prompt.executor.clients.bedrock

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import kotlinx.datetime.Clock

/**
 * Configuration settings for connecting to the AWS Bedrock API.
 *
 * @property region The AWS region where Bedrock service is hosted.
 * @property timeoutConfig Configuration for connection timeouts.
 * @property endpointUrl Optional custom endpoint URL for testing or private deployments.
 * @property maxRetries Maximum number of retries for failed requests.
 * @property enableLogging Whether to enable detailed AWS SDK logging.
 */
public class BedrockClientSettings(
    public val region: String = "us-east-1",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    public val endpointUrl: String? = null,
    public val maxRetries: Int = 3,
    public val enableLogging: Boolean = false
)

/**
 * Internal credentials provider for AWS Bedrock.
 * This is an expect class that will have platform-specific implementations.
 */
internal expect class StaticCredentialsProvider(
    awsAccessKeyId: String,
    awsSecretAccessKey: String
)

/**
 * Factory function to create a BedrockLLMClient instance.
 * This is an expect function with platform-specific implementations.
 *
 * @param awsAccessKeyId Your AWS Access Key ID.
 * @param awsSecretAccessKey Your AWS Secret Access Key.
 * @param settings Custom client settings for region and timeouts.
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public expect fun createBedrockLLMClient(
    awsAccessKeyId: String,
    awsSecretAccessKey: String,
    settings: BedrockClientSettings = BedrockClientSettings(),
    clock: Clock = Clock.System
): LLMClient
