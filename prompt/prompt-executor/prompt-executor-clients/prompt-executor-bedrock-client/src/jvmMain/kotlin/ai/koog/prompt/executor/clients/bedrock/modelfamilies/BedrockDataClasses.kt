package ai.koog.prompt.executor.clients.bedrock.modelfamilies

import ai.koog.prompt.executor.clients.anthropic.AnthropicResponseContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

/**
 * Represents the model to invoke an Anthropic service using Bedrock.
 * refer to https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages-request-response.html
 * @property anthropicVersion The version of the Anthropic API or service being invoked.
 * @property maxTokens The maximum number of tokens to generate in the response. default to 4k tokens because of Claude Haiku 3, refer to https://docs.anthropic.com/en/docs/about-claude/models/overview#model-comparison
 */
@Serializable
public data class BedrockAnthropicInvokeModel(
    @SerialName("anthropic_version")
    val anthropicVersion: String = "bedrock-2023-05-31", // The anthropic version. The value must be bedrock-2023-05-31. See https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages-request-response.html
    @SerialName("max_tokens")
    val maxTokens: Int = MAX_TOKENS_DEFAULT,
    val system: String = "",
    val temperature: Double? = 1.0,
    val messages: List<BedrockAnthropicInvokeModelMessage> = emptyList(),
    val tools: List<BedrockAnthropicInvokeModelTool>? = null,
    @SerialName("tool_choice")
    val toolChoice: BedrockAnthropicToolChoice? = null,
) {
    /**
     * Provides shared logic and utility functions for managing and interacting with the
     * Bedrock Anthropic service model. This companion object includes methods and constants
     * that are central to constructing and handling request payloads, ensuring adherence
     * to Bedrock-specific requirements for the Anthropic Claude model family.
     */
    public companion object {
        /**
         * The maximum number of tokens to generate in the response.
         * default to 4k tokens because of Claude Haiku 3, refer to https://docs.anthropic.com/en/docs/about-claude/models/overview#model-comparison
         */
        public const val MAX_TOKENS_DEFAULT: Int = 4000
    }
}

/**
 * Data class representing a message used to invoke a model in the Bedrock Anthropic API.
 *
 * @property role The role of the message sender, such as "user", "assistant", or another predefined role.
 * @property content The content of the message, encapsulated in a `BedrockAnthropicInvokeModelTextContent` object.
 */
@Serializable
public data class BedrockAnthropicInvokeModelMessage(
    val role: String,
    val content: List<BedrockAnthropicInvokeModelTextContent>,
)

/**
 * Represents a tool used to interact with the Bedrock Anthropic model.
 *
 * This data class is used to describe a tool with specific properties such as type, name,
 * description, and input schema.
 *
 * @property type Identifies the type of the tool. The default value is "custom".
 * @property name The name of the tool.
 * @property description An optional description of the tool, providing additional context.
 * @property inputSchema An optional JSON schema that describes the input structure for the tool.
 */
@Serializable
public data class BedrockAnthropicInvokeModelTool(
    val type: String = "custom",
    val name: String,
    val description: String? = null,
    @SerialName("input_schema")
    val inputSchema: JsonObject? = null,
)

/**
 * Represents the text content of a model invocation message for the Bedrock Anthropic API.
 *
 * This data class is intended to encapsulate the content of a message that is sent to or received from a model.
 * It is used along with the `BedrockAnthropicInvokeModelMessage` class to provide structured message content.
 *
 * @property type The type of the content. Defaults to "text".
 * @property text The actual text content of the message.
 */
@Serializable
public data class BedrockAnthropicInvokeModelTextContent(
    val type: String = "text",
    val text: String,
)

/**
 * Represents the tool choice configuration for Anthropic via Bedrock.
 *
 * @property type The selection strategy: "auto", "any", "tool", or "none".
 * @property name Optional tool name when type is "tool".
 */
@Serializable
public data class BedrockAnthropicToolChoice(
    val type: String,
    val name: String? = null,
)

/**
 * Represents a response from Anthropic's API as processed by Bedrock.
 *
 * @property id The unique identifier of the response.
 * @property type The type of the response.
 * @property role The role associated with the response, e.g., "assistant" or "user".
 * @property content A list of structured content objects associated with the response, such as text or tool use.
 * @property model The name or identifier of the Anthropic model used to generate the response.
 * @property stopReason An optional field that describes why the generation of the response stopped.
 * @property usage An optional field representing usage statistics, such as input and output token counts.
 */
@Serializable
public data class BedrockAnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicResponseContent>,
    val model: String,
    @JsonNames("stopReason", "stop_reason")
    val stopReason: String? = null,
    val usage: BedrockAnthropicUsage? = null
)

/**
 * Represents the token usage data for a request or transaction in the BedrockAnthropic API.
 *
 * This class is serialized using kotlinx.serialization and contains information about the number
 * of tokens processed in both directions: input and output.
 *
 * @property inputTokens The number of tokens sent as input.
 * @property outputTokens The number of tokens received as output.
 */
@Serializable
public data class BedrockAnthropicUsage(
    @SerialName("input_tokens")
    @JsonNames("inputTokens", "input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    @JsonNames("outputTokens", "output_tokens")
    val outputTokens: Int
)
