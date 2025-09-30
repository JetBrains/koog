package ai.koog.agents.core.feature.handler

/**
 * Represents the context for handling LLM-specific events within the framework.
 */
public interface LLMEventHandlerContext : EventHandlerContext

/**
 * Represents the context for handling a before LLM call event.
 */
@Deprecated(
    message = "Use LLMCallStartingContext instead",
    replaceWith = ReplaceWith(
        expression = "LLMCallStartingContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext")
    )
)
public typealias BeforeLLMCallContext = ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext

/**
 * Represents the context for handling an after LLM call event.
 */
@Deprecated(
    message = "Use LLMCallCompletedContext instead",
    replaceWith = ReplaceWith(
        expression = "LLMCallCompletedContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext")
    )
)
public typealias AfterLLMCallContext = ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
