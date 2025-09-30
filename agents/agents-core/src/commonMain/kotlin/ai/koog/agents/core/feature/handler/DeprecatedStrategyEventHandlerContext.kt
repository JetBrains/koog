package ai.koog.agents.core.feature.handler

/**
 * Defines the context specifically for handling strategy-related events within the AI agent framework.
 * Extends the base event handler context to include functionality and behavior dedicated to managing
 * the lifecycle and operations of strategies associated with AI agents.
 */
@Deprecated(
    message = "Use StrategyEventContext instead",
    replaceWith = ReplaceWith(
        expression = "StrategyEventContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.strategy.StrategyEventContext")
    )
)
public typealias StrategyEventHandlerContext = ai.koog.agents.core.feature.handler.strategy.StrategyEventContext

/**
 * Represents the context for starting AI agent strategies during execution.
 */
@Deprecated(
    message = "Use StrategyStartingContext instead",
    replaceWith = ReplaceWith(
        expression = "StrategyStartingContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext")
    )
)
public typealias StrategyStartContext<TFeature> = ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext<TFeature>

/**
 * Represents the context associated with the completion of an AI agent strategy execution.
 */
@Deprecated(
    message = "Use StrategyCompletedContext instead",
    replaceWith = ReplaceWith(
        expression = "StrategyCompletedContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext")
    )
)
public typealias StrategyFinishedContext<TFeature> = ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext<TFeature>
