package ai.koog.agent.koog

import ai.koog.agent.FlowAgent
import ai.koog.agent.FlowAgentConfig
import ai.koog.agent.FlowAgentKind
import ai.koog.model.FlowAgentInput
import ai.koog.model.FlowAgentPrompt

/**
 * Base class for Koog flow agents that provides common functionality.
 */
public abstract class KoogFlowAgent(
    override val name: String,
    override val type: FlowAgentKind,
    override val model: String? = null,
    override val prompt: FlowAgentPrompt? = null,
    override val input: FlowAgentInput,
    override val config: FlowAgentConfig
) : FlowAgent
