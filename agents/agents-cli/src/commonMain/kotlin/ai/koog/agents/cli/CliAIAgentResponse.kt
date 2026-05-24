package ai.koog.agents.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents the usage information from a CLI agent.
 *
 * @param inputTokensCount The number of tokens used in the input.
 * @param outputTokensCount The number of tokens generated in the output.
 * @param metadata Free-form information associated with a response from a cli agent.
 */
@Serializable
public data class CliAgentResponseMetaInfo(
    val inputTokensCount: Int? = null,
    val outputTokensCount: Int? = null,
    val metadata: JsonObject? = null
)

/**
 * Represents the response from a CLI agent.
 *
 * @property content The full content (e.g., stdout) of the agent execution.
 * @property isError Whether the agent execution resulted in an error.
 * @property metaInfo Usage information about the agent execution.
 */
@Serializable
public data class CliAIAgentResponse(
    val content: String,
    val isError: Boolean,
    val metaInfo: CliAgentResponseMetaInfo,
)

/**
 * Represents a container for structured data from a CLI agent.
 *
 * @param T The type of the structured data.
 * @property structuredResult The parsed structured data.
 * @property response The original response from which the result was parsed.
 */
@Serializable
public data class CliAgentStructuredResponse<out T>(
    val structuredResult: T?,
    val response: CliAIAgentResponse
)
