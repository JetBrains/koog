package ai.koog.agents.core.feature.handler

/**
 * A handler class for managing strategy-related events, providing callbacks for when strategies
 * are started or finished. It is designed to operate on a specific feature type and delegate
 * event handling to the assigned handlers.
 *
 * @param FeatureT The type of feature associated with the strategy operations.
 * @property feature The specific feature instance associated with this handler.
 */
public class StrategyHandler<FeatureT : Any>(public val feature: FeatureT) {

    /**
     * Handler invoked when a strategy is started. This can be used to perform custom logic
     * related to strategy initiation for a specific feature.
     */
    public var strategyStartedHandler: StrategyStartedHandler<FeatureT> =
        StrategyStartedHandler { context -> }

    /**
     * A handler for processing the completion of a strategy within the context of a feature update.
     *
     * This variable delegates strategy completion events to a custom implementation defined by the
     * `StrategyFinishedHandler` functional interface. It is invoked when a strategy processing is finalized,
     * providing the necessary context and the result of the operation.
     *
     * You can customize the behavior of this handler by assigning an instance of
     * `StrategyFinishedHandler` that defines how the completion logic should be handled.
     *
     * @see StrategyFinishedHandler
     * @see StrategyHandler.handleStrategyFinished
     */
    public var strategyFinishedHandler: StrategyFinishedHandler<FeatureT> =
        StrategyFinishedHandler { context, result -> }

    /**
     * Handles strategy starts events by delegating to the handler.
     *
     * @param context The context for updating the agent with the feature
     */
    public suspend fun handleStrategyStarted(context: StrategyUpdateContext<FeatureT>) {
        strategyStartedHandler.handle(context)
    }

    /**
     * Internal API for handling strategy start events with type casting.
     *
     * @param context The context for updating the agent
     */
    @Suppress("UNCHECKED_CAST")
    public suspend fun handleStrategyStartedUnsafe(context: StrategyUpdateContext<*>) {
        handleStrategyStarted(context as StrategyUpdateContext<FeatureT>)
    }

    /**
     * Handles strategy finish events by delegating to the handler.
     *
     * @param context The context for updating the agent with the feature
     */
    public suspend fun handleStrategyFinished(context: StrategyUpdateContext<FeatureT>, result: String) {
        strategyFinishedHandler.handle(context, result)
    }

    /**
     * Internal API for handling strategy finish events with type casting.
     *
     * @param context The context for updating the agent
     */
    @Suppress("UNCHECKED_CAST")
    public suspend fun handleStrategyFinishedUnsafe(context: StrategyUpdateContext<*>, result: String) {
        handleStrategyFinished(context as StrategyUpdateContext<FeatureT>, result)
    }
}

/**
 * A functional interface for handling start events of an AI agent strategy.
 *
 * @param FeatureT The type of feature associated with the strategy.
 */
public fun interface StrategyStartedHandler<FeatureT : Any> {
    /**
     * Handles the processing of a strategy update within a specified context.
     *
     * @param context The context for the strategy update, encapsulating the strategy,
     *                session identifier, and feature associated with the handling process.
     */
    public suspend fun handle(context: StrategyUpdateContext<FeatureT>)
}

/**
 * Functional interface representing a handler invoked when a strategy execution is finished.
 *
 * @param FeatureT The type of the feature tied to the strategy.
 */
public fun interface StrategyFinishedHandler<FeatureT : Any> {
    /**
     * Handles the completion of a strategy update process by processing the given result and its related context.
     *
     * @param context The context of the strategy update, containing details about the current strategy,
     *                the session, and the feature associated with the update.
     * @param result A string representing the outcome or result of the strategy update process.
     */
    public suspend fun handle(context: StrategyUpdateContext<FeatureT>, result: String)
}
