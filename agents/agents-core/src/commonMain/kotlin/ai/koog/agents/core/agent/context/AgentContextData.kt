@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.planner.PlannerAgentExecutionPoint
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONNull
import ai.koog.serialization.JSONObject

@InternalAgentsApi
public sealed class AgentContextData {
    internal abstract val messageHistory: List<Message>
    internal abstract val llmParams: LLMParams?
    internal abstract val llmModel: LLModel?
    internal abstract val tools: List<String>?
    internal abstract val storage: JSONObject
    internal abstract val agentIterations: Int
    internal abstract val rollbackStrategy: RollbackStrategy
    internal abstract val additionalRollbackActions: suspend (AIAgentContext) -> Unit
}

@InternalAgentsApi
public class GraphAgentContextData(
    override val messageHistory: List<Message>,
    override val llmParams: LLMParams?,
    override val llmModel: LLModel?,
    override val tools: List<String>?,
    override val storage: JSONObject,
    override val agentIterations: Int,
    internal val nodePath: String,
    @Deprecated("Use lastOutput instead, lastOutput will be removed in future versions")
    internal val lastInput: JSONElement = JSONNull,
    internal val lastOutput: JSONElement = JSONNull,
    override val rollbackStrategy: RollbackStrategy,
    override val additionalRollbackActions: suspend (AIAgentContext) -> Unit = {}
) : AgentContextData() {
    init {
        require(lastInput == JSONNull || lastOutput == JSONNull) { "`lastInput` and `lastOutput` cannot be both set" }
        require(lastInput == JSONNull || lastOutput == JSONNull) { "`lastInput` (until 0.6.0) or `lastOutput` (since 0.6.1) must be set" }
    }
}

@InternalAgentsApi
public class PlannerAgentContextData(
    override val messageHistory: List<Message>,
    internal val state: JSONElement,
    internal val plan: JSONElement,
    override val llmParams: LLMParams?,
    override val llmModel: LLModel?,
    override val tools: List<String>?,
    override val storage: JSONObject,
    override val agentIterations: Int,
    internal val executionPoint: PlannerAgentExecutionPoint,
    override val rollbackStrategy: RollbackStrategy,
    override val additionalRollbackActions: suspend (AIAgentContext) -> Unit = {}
) : AgentContextData()

public enum class RollbackStrategy {
    /**
     * Rollback state of the agent to the last saved state in full.
     * Meaning restore the entire context, including message history and any other stateful data.
     */
    Default,

    /**
     * Rollback only the message history to the last saved state.
     * Agent starts from the first node with saved message history.
     */
    MessageHistoryOnly,
}
