package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

/**
 * Represents the context for handling LLM-specific events within the framework.
 */
public interface LLMEventHandlerContext : EventHandlerContext

/**
 * Represents the context for handling a before LLM call event.
 *
 * @property prompt The prompt that will be sent to the language model.
 * @property tools The list of tool descriptors available for the LLM call.
 * @property model The language model instance being used.
 */
public data class BeforeLLMCallContext(
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>,
) : LLMEventHandlerContext {
    override val eventType: AgentEventType = AgentEventType.BeforeLLMCall
}

/**
 * Represents the context for handling an after LLM call event.
 *
 * @property prompt The prompt that was sent to the language model.
 * @property tools The list of tool descriptors that were available for the LLM call.
 * @property model The language model instance that was used.
 * @property responses The response messages received from the language model.
 */
public data class AfterLLMCallContext(
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>,
    val responses: List<Message.Response>,
    val moderationResponse: ModerationResult?
) : LLMEventHandlerContext {
    override val eventType: AgentEventType = AgentEventType.AfterLLMCall
}
