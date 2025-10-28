package ai.koog.agents.core.feature.handler.strategy

/**
 * A handler class for managing strategy-related events, providing callbacks for when strategies
 * are started or finished. It is designed to operate on a specific feature type and delegate
 * event handling to the assigned handlers.
 */
public class StrategyEventHandler {

    /**
     * Handler invoked when a strategy is started. This can be used to perform custom logic
     * related to strategy initiation for a specific feature.
     */
    public var strategyStartingHandler: StrategyStartingHandler =
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
    public var strategyCompletedHandler: StrategyCompletedHandler =
        StrategyCompletedHandler { _ -> }

    /**
     * Handles strategy starts events by delegating to the handler.
     *
     * @param context The context for updating the agent with the feature
     */
    public suspend fun handleStrategyStarting(context: StrategyStartingContext) {
        strategyStartingHandler.handle(context)
    }

    /**
     * Handles strategy finish events by delegating to the handler.
     *
     * @param context The context for updating the agent with the feature
     */
    public suspend fun handleStrategyCompleted(context: StrategyCompletedContext) {
        strategyCompletedHandler.handle(context)
    }
}

/**
 * A functional interface for handling start events of an AI agent strategy.
 */
public fun interface StrategyStartingHandler {
    /**
     * Handles the processing of a strategy update within a specified context.
     *
     * @param context The context for the strategy update, encapsulating the strategy,
     *                run identifier, and feature associated with the handling process.
     */
    public suspend fun handle(context: StrategyStartingContext)
}

/**
 * Functional interface representing a handler invoked when a strategy execution is finished.
 */
public fun interface StrategyCompletedHandler {
    /**
     * Handles the completion of a strategy update process by processing the given result and its related context.
     *
     * @param context The context of the strategy update, containing details about the current strategy,
     *                the session, and the feature associated with the update.
     */
    public suspend fun handle(context: StrategyCompletedContext)
}
