package ai.koog.prompt.executor.clients.bedrock

import kotlinx.serialization.Serializable

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

}
