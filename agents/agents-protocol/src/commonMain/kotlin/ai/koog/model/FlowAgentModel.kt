package ai.koog.model

import ai.koog.agent.FlowAgentConfig
import ai.koog.agent.FlowAgentKind
import ai.koog.agent.ToolChoiceKind
import kotlinx.serialization.Serializable

/**
 * agent:
 *      name: string
 * 	    type: agent.type
 * 	    provider: agent.provider
 * 	    config: agent.config
 * 	    runtime: agent.runtime
 * 	    input: agent.input
 * 	    output: agent.output
 * 	    features: [agent.feature]
 */
@Serializable
public data class FlowAgentModel(
    val name: String,
    val type: FlowAgentKind,
    val model: String? = null,
    val runtime: String? = null,
    val prompt: FlowAgentPrompt? = null,
    val input: FlowAgentInput? = null,
    val config: FlowAgentConfigModel? = null
)

/**
 * config:
 *      model: string,
 *      temperature: number
 *      max_iterations: number
 *      max_tokens: number
 *      top_p: number
 * 	    tool_choice: agent.config.tool_choice
 * 	    speculation: string
 */
@Serializable
public data class FlowAgentConfigModel(
    val model: String? = null,
    val temperature: Double? = null,
    val maxIterations: Int? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val toolChoice: ToolChoiceKind? = null,
    val speculation: String? = null,
)

public fun FlowAgentConfigModel?.toFlowAgentConfig(): FlowAgentConfig =
    this?.let {
        FlowAgentConfig(
            model = model,
            temperature = temperature,
            maxIterations = maxIterations,
            maxTokens = maxTokens,
            topP = topP,
            toolChoice = toolChoice,
            speculation = speculation
        )
    } ?: FlowAgentConfig()

/**
 *
 */
@Serializable
public data class FlowAgentPrompt(
    val system: String,
    val user: String? = null
)

/**
 * Represents input for a flow agent.
 */
@Serializable
public data class FlowAgentInput(
    val task: String? = null,
    val transformations: List<Transformation>? = null
)


