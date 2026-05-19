package ai.koog.agents.core.feature.handler.llm

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

/**
 * Represents the context for handling LLM-specific events within the framework.
 */
public interface LLMCallEventContext : AgentLifecycleEventContext {
    /**
     * The AI agent context.
     */
    public val context: AIAgentContext

    /**
     * The unique identifier for this LLM call session.
     */
    public val runId: String

    /**
     * The prompt that will be sent to the language model.
     */
    public val prompt: Prompt

    /**
     * The language model instance being used.
     */
    public val model: LLModel

    /**
     * The list of tool descriptors available for the LLM call.
     */
    public val tools: List<ToolDescriptor>
}

/**
 * Represents the context for handling a before LLM call event.
 */
public data class LLMCallStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val runId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : LLMCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMCallStarting
}

/**
 * Represents the context for handling an after LLM call failed.
 *
 * @property error The error that occurred during the LLM call.
 */
public data class LLMCallFailedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val runId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    public val error: Throwable
) : LLMCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMCallStarting
}

/**
 * Represents the context for handling an after LLM call event.
 *
 * @property response The response message received from the language model.
 * @property moderationResponse The moderation response, if any, received from the language model.
 */
public data class LLMCallCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val runId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    public val response: Message.Assistant?,
    public val moderationResponse: ModerationResult?,
) : LLMCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMCallCompleted
}
