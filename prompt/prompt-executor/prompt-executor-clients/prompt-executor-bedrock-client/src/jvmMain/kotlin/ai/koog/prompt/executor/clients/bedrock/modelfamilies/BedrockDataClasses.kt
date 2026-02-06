package ai.koog.prompt.executor.clients.bedrock.modelfamilies

import ai.koog.prompt.executor.clients.anthropic.models.AnthropicContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Represents the model to invoke an Anthropic service using Bedrock.
 * refer to https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages-request-response.html
 * @property anthropicVersion The version of the Anthropic API or service being invoked.
 * @property maxTokens The maximum number of tokens to generate in the response. default to 4k tokens because of Claude Haiku 3, refer to https://docs.anthropic.com/en/docs/about-claude/models/overview#model-comparison
 */
@Serializable
public data class BedrockAnthropicInvokeModel(
    @SerialName("anthropic_version") val anthropicVersion: String = "bedrock-2023-05-31", // The anthropic version. The value must be bedrock-2023-05-31. See https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages-request-response.html
    @SerialName("max_tokens") val maxTokens: Int = MAX_TOKENS_DEFAULT,
    val system: String = "",
    val temperature: Double? = 1.0,
    val messages: List<BedrockAnthropicInvokeModelMessage> = emptyList(),
    val tools: List<BedrockAnthropicInvokeModelTool>? = null,
    @SerialName("tool_choice") val toolChoice: BedrockAnthropicToolChoice? = null,
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
 * @property content The content of the message, encapsulated in a `BedrockAnthropicInvokeModelTextContent` object.
 */
@Serializable
@JsonClassDiscriminator("role")
public sealed interface BedrockAnthropicInvokeModelMessage {
    /**
     * A list of content elements that form the message, where each content
     * can be of varying types like text, image, document, tool usage, or tool result.
     */
    public val content: List<BedrockAnthropicInvokeModelContent>

    /**
     * User BedrockAnthropic message.
     */
    @Serializable
    @SerialName("user")
    public data class User(override val content: List<BedrockAnthropicInvokeModelContent>) :
        BedrockAnthropicInvokeModelMessage

    /**
     * Assistant BedrockAnthropic message.
     */
    @Serializable
    @SerialName("assistant")
    public data class Assistant(override val content: List<BedrockAnthropicInvokeModelContent>) :
        BedrockAnthropicInvokeModelMessage
}

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
    @SerialName("input_schema") val inputSchema: JsonObject? = null,
)

/**
 * Represents a class for constructing various types of content used in invoking models
 * via the Bedrock Anthropic API.
 *
 * This sealed class acts as the base type for different kinds of invocation-related content such as
 * textual responses, tool results, and tool call instructions. It enables a structured approach to
 * managing the diverse data types utilized in interactions with the Bedrock Anthropic API.
 */
@Serializable
public sealed interface BedrockAnthropicInvokeModelContent {
    /**
     * Represents the text content in a model invocation for the Bedrock Anthropic API.
     *
     * This class extends the `BedrockAnthropicInvokeModelContent` and is used to encapsulate the text
     * response or content associated with the invocation process.
     *
     * @property text The textual content associated with this invocation.
     */
    @Serializable
    @SerialName("text")
    public class Text(public val text: String) : BedrockAnthropicInvokeModelContent

    /**
     * Represents a thinking process.
     *
     * This class captures the model's reasoning and thought process during its operation.
     * It is serialized with the discriminator "thinking" for polymorphic serialization.
     *
     * @property signature An identifier or signature associated with this thought process.
     * @property thinking The actual content of the model's internal reasoning or thought process.
     */
    @Serializable
    @SerialName("thinking")
    public data class Thinking(val signature: String, val thinking: String) : BedrockAnthropicInvokeModelContent

    /**
     * Represents the result of a tool invocation in the context of Bedrock's Anthropic API.
     *
     * This class is a specialized type of `BedrockAnthropicInvokeModelContent`, intended to encapsulate
     * the response from a tool invoked as part of the model's execution. It contains metadata about the
     * tool's usage and the returned content.
     *
     * @property toolUseId The unique identifier for the specific tool invocation. This can be used to trace or debug the usage of tools.
     * @property content The content or result returned by the tool after execution.
     */
    @Serializable
    @SerialName("tool_result")
    public class ToolResult(
        @SerialName("tool_use_id") public val toolUseId: String,
        public val content: String
    ) : BedrockAnthropicInvokeModelContent

    /**
     * Represents a tool call instruction which is part of the model invocation content.
     *
     * This class is designed to capture the details of a tool usage scenario during the communication
     * or execution process. A ToolCall includes an identifier, the name of the tool being invoked,
     * and the specific input provided to the tool in the form of JSON objects.
     *
     * The class is serializable using kotlinx.serialization and uses a custom serial name `tool_use`.
     *
     * @property id The unique identifier for the tool call invocation.
     * @property name The name of the tool being invoked.
     * @property input A JSON object containing the input parameters or configuration for the tool.
     */
    @Serializable
    @SerialName("tool_use")
    public class ToolCall(public val id: String, public val name: String, public val input: JsonElement) :
        BedrockAnthropicInvokeModelContent
}

/**
 * Represents the result of invoking a Bedrock Anthropic model tool.
 *
 * This data class contains the essential information returned by the tool invocation process.
 *
 * @property type The type of the result, indicating its purpose or category.
 * @property toolUseId An identifier associated with the tool usage, allowing correlation with specific tool executions.
 * @property content The content or output generated from the tool invocation.
 */
public data class BedrockAnthropicInvokeModelToolResultContent(
    val type: String = "tool_result",
    @SerialName("tool_use_id") val toolUseId: String,
    val content: String,
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
    val content: List<AnthropicContent>,
    val model: String,
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
    val inputTokens: Int,
    val outputTokens: Int
)
