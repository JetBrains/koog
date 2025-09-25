package ai.koog.agents.core.feature.handler.strategy

import ai.koog.agents.core.annotation.InternalAgentsApi

/**
 * A handler class for managing strategy-related events, providing callbacks for when strategies
 * are started or finished. It is designed to operate on a specific feature type and delegate
 * event handling to the assigned handlers.
 *
 * @param TFeature The type of feature associated with the strategy operations.
 * @property feature The specific feature instance associated with this handler.
 */
public class StrategyEventHandler<TFeature : Any>(public val feature: TFeature) {

    /**
     * Handler invoked when a strategy is started. This can be used to perform custom logic
     * related to strategy initiation for a specific feature.
     */
    public var strategyStartingHandler: StrategyStartingHandler<TFeature> =
        StrategyStartingHandler { _ -> }

    /**
     * A handler for processing the completion of a strategy within the context of a feature update.
     *
     * This variable delegates strategy completion events to a custom implementation defined by the
     * `StrategyFinishedHandler` functional interface. It is invoked when a strategy processing is finalized,
     * providing the necessary context and the result of the operation.
     *
     * You can customize the behavior of this handler by assigning an instance of
     * `StrategyFinishedHandler` that defines how the completion logic should be handled.
     */
    public var strategyCompletedHandler: StrategyCompletedHandler<TFeature> =
        StrategyCompletedHandler { _ -> }

    /**
     * Handles strategy starts events by delegating to the handler.
     *
     * @param context The context for updating the agent with the feature
     */
    public suspend fun handleStrategyStarting(context: StrategyStartingContext<TFeature>) {
        strategyStartingHandler.handle(context)
    }

    /**
     * Internal API for handling strategy start events with type casting.
     *
     * @param context The context for updating the agent
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    public suspend fun handleStrategyStartingUnsafe(context: StrategyStartingContext<*>) {
        handleStrategyStarting(context as StrategyStartingContext<TFeature>)
    }

    /**
     * Handles strategy finish events by delegating to the handler.
     *
     * @param context The context for updating the agent with the feature
     */
    public suspend fun handleStrategyCompleted(context: StrategyCompletedContext<TFeature>) {
        strategyCompletedHandler.handle(context)
    }

    /**
     * Internal API for handling strategy finish events with type casting.
     *
     * @param context The context for updating the agent
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    public suspend fun handleStrategyCompletedUnsafe(context: StrategyCompletedContext<*>) {
        handleStrategyCompleted(context as StrategyCompletedContext<TFeature>)
    }
}

/**
 * A functional interface for handling start events of an AI agent strategy.
 *
 * @param FeatureT The type of feature associated with the strategy.
 */
public fun interface StrategyStartingHandler<FeatureT : Any> {
    /**
     * Handles the processing of a strategy update within a specified context.
     *
     * @param context The context for the strategy update, encapsulating the strategy,
     *                run identifier, and feature associated with the handling process.
     */
    public suspend fun handle(context: StrategyStartingContext<FeatureT>)
}

/**
 * Functional interface representing a handler invoked when a strategy execution is finished.
 *
 * @param TFeature The type of the feature tied to the strategy.
 */
public fun interface StrategyCompletedHandler<TFeature : Any> {
    /**
     * Handles the completion of a strategy update process by processing the given result and its related context.
     *
     * @param context The context of the strategy update, containing details about the current strategy,
     *                the session, and the feature associated with the update.
     */
    public suspend fun handle(context: StrategyCompletedContext<TFeature>)
}
