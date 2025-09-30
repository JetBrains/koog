package ai.koog.agents.core.feature.handler

/**
 * Provides the context for handling events specific to AI agents.
 * This interface extends the foundational event handling context, `EventHandlerContext`,
 * and is specialized for scenarios involving agents and their associated workflows or features.
 *
 * The `AgentEventHandlerContext` enables implementation of event-driven systems within
 * the AI Agent framework by offering hooks for custom event handling logic tailored to agent operations.
 */
@Deprecated(
    message = "Use AgentEventContext instead",
    replaceWith = ReplaceWith("AgentEventContext", "ai.koog.agents.core.feature.handler.agent.AgentEventContext")
)
public typealias AgentEventHandlerContext = ai.koog.agents.core.feature.handler.agent.AgentEventContext

/**
 * Provides a context for executing transformations and operations within an AI agent's environment.
 */
@Deprecated(
    message = "Use AgentEnvironmentTransformingContext instead",
    replaceWith = ReplaceWith("AgentEnvironmentTransformingContext", "ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingContext")
)
public typealias AgentTransformEnvironmentContext<TFeature> = ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingContext<TFeature>

/**
 * Represents the context available during the start of an AI agent.
 */
@Deprecated(
    message = "Use AgentStartingContext instead",
    replaceWith = ReplaceWith("AgentStartingContext", "ai.koog.agents.core.feature.handler.agent.AgentStartingContext")
)
public typealias AgentStartContext<TFeature> = ai.koog.agents.core.feature.handler.agent.AgentStartingContext<TFeature>

/**
 * Represents the context for handling the completion of an agent's execution.
 */
@Deprecated(
    message = "Use AgentCompletedContext instead",
    replaceWith = ReplaceWith("AgentCompletedContext", "ai.koog.agents.core.feature.handler.agent.AgentCompletedContext")
)
public typealias AgentFinishedContext = ai.koog.agents.core.feature.handler.agent.AgentCompletedContext

/**
 * Represents the context for handling errors that occur during the execution of an agent run.
 */
@Deprecated(
    message = "Use AgentExecutionFailedContext instead",
    replaceWith = ReplaceWith("AgentExecutionFailedContext", "ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext")
)
public typealias AgentRunErrorContext = ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext

/**
 * Represents the context passed to the handler that is executed before an agent is closed.
 */
@Deprecated(
    message = "Use AgentClosingContext instead",
    replaceWith = ReplaceWith("AgentClosingContext", "ai.koog.agents.core.feature.handler.agent.AgentClosingContext")
)
public typealias AgentBeforeCloseContext = ai.koog.agents.core.feature.handler.agent.AgentClosingContext
