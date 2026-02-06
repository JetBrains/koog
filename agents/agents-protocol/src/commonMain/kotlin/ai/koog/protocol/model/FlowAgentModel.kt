package ai.koog.protocol.model

import ai.koog.protocol.agent.FlowAgentConfig
import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.FlowAgentPrompt
import ai.koog.protocol.agent.FlowAgentRuntimeKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Serializable model representing an agent configuration in a flow.
 *
 * An agent performs a specific task within a flow, such as executing LLM requests,
 * transforming data, or validating results.
 *
 * @property name Unique identifier for the agent within the flow
 * @property type The kind of agent (task, verify, transform, or parallel)
 * @property model Optional LLM model identifier. If not specified, falls back to the flow's defaultModel
 * @property runtime Optional runtime environment (defaults to KOOG if not specified)
 * @property config Optional configuration for temperature, max iterations, tool choice, etc.
 * @property prompt Optional prompt templates (system and user messages)
 * @property params Agent-specific parameters as a JSON object (e.g., task description, tool names)
 * @property output Optional output schema definition for structured responses
 */
@Serializable
public data class FlowAgentModel(
    val name: String,
    val type: FlowAgentKind,
    val model: String? = null,
    val runtime: FlowAgentRuntimeKind? = null,
    val config: FlowAgentConfig? = null,
    val prompt: FlowAgentPrompt? = null,
    val params: JsonObject? = null,
    val output: FlowAgentOutputModel? = null,
)

/**
 * Serializable model for agent prompt configuration.
 *
 * Defines the prompt templates used by an agent when interacting with an LLM.
 *
 * @property system The system prompt that sets the agent's behavior and context
 * @property user Optional user prompt template for additional instructions
 */
@Serializable
public data class FlowAgentPromptModel(
    val system: String,
    val user: String? = null
)

/**
 * Serializable model for agent output schema configuration.
 *
 * Specifies the expected structure of the agent's output, enabling structured data extraction.
 *
 * @property schema JSON schema string defining the expected output format
 */
@Serializable
public data class FlowAgentOutputModel(
    val schema: String
)
