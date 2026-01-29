package ai.koog.agents.core.feature.handler.llm

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

/**
 * Represents the context for handling LLM-specific events within the framework.
 */
public interface LLMCallEventContext : AgentLifecycleEventContext {
    public val runId: String
    public val prompt: Prompt
    public val model: LLModel
    public val tools: List<ToolDescriptor>
    public val context: AIAgentContext
}

/**
 * Represents the context for handling a before LLM call event.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property runId The unique identifier for this LLM call session.
 * @property prompt The prompt that will be sent to the language model.
 * @property model The language model instance being used.
 * @property tools The list of tool descriptors available for the LLM call.
 */
public data class LLMCallStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val runId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    override val context: AIAgentContext
) : LLMCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMCallStarting
}

/**
 * Represents the context for handling a after LLM call failed.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property runId The unique identifier for this LLM call session.
 * @property prompt The prompt that will be sent to the language model.
 * @property model The language model instance being used.
 * @property tools The list of tool descriptors available for the LLM call.
 */
public data class LLMCallFailedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val runId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    override val context: AIAgentContext,
    val error: Throwable
) : LLMCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMCallStarting
}

/**
 * Represents the context for handling an after LLM call event.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property runId The unique identifier for this LLM call session.
 * @property prompt The prompt that was sent to the language model.
 * @property model The language model instance that was used.
 * @property tools The list of tool descriptors that were available for the LLM call.
 * @property responses The response messages received from the language model.
 * @property moderationResponse The moderation response, if any, received from the language model.
 */
public data class LLMCallCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val runId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    val responses: List<Message.Response>,
    val moderationResponse: ModerationResult?,
    override val context: AIAgentContext
) : LLMCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMCallCompleted
}
