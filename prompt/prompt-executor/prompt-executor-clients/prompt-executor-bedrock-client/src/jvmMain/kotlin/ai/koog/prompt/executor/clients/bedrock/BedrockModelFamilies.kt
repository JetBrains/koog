package ai.koog.prompt.executor.clients.bedrock

import kotlinx.serialization.Serializable

/**
 * Represents a sealed class for AWS Bedrock model families.
 *
 * This class serves as a hierarchy for different sub-providers under the AWS Bedrock ecosystem.
 * Each sub-provider is represented as a specific sealed `data object` with an associated
 * unique identifier and display name.
 *
 * @property id The unique identifier of the Bedrock model family.
 * @property display The human-readable display name of the Bedrock model family.
 */
@Serializable
public sealed class BedrockModelFamilies(
    public val id: String,
    public val display: String
) {

    /**
     * Represents the Anthropic sub-provider under AWS Bedrock.
     */
    @Serializable
    public data object AnthropicClaude : BedrockModelFamilies("bedrock.anthropic", "AWS Bedrock (Anthropic Claude)")

    /**
     * Represents the Amazon sub-provider under AWS Bedrock.
     */
    @Serializable
    public data object AmazonNova : BedrockModelFamilies("bedrock.amazon", "AWS Bedrock (Amazon Nova)")

    /**
     * Represents the AI21 sub-provider under AWS Bedrock.
     */
    @Serializable
    public data object AI21Jamba : BedrockModelFamilies("bedrock.ai21", "AWS Bedrock (AI21 Jamba)")

    /**
     * Represents the Meta sub-provider under AWS Bedrock.
     */
    @Serializable
    public data object Meta : BedrockModelFamilies("bedrock.meta", "AWS Bedrock (Meta Llama)")

    /**
     * Represents the Amazon Titan sub-provider under AWS Bedrock.
     */
    @Serializable
    public data object TitanEmbedding : BedrockModelFamilies("bedrock.titan", "AWS Bedrock (Amazon Titan Embedding)")

    /**
     * Represents the Cohere sub-provider under AWS Bedrock.
     */
    @Serializable
    public data object Cohere : BedrockModelFamilies("bedrock.cohere", "AWS Bedrock (Cohere Embeddings)")
}
