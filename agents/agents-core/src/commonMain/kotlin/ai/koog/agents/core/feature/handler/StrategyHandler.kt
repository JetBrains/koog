package ai.koog.agents.core.feature.handler

public class StrategyHandler<FeatureT : Any>(public val feature: FeatureT) {

    /**
     * Handler invoked when a strategy is started. This can be used to perform custom logic
     * related to strategy initiation for a specific feature.
     */
    public var strategyStartedHandler: StrategyStartedHandler<FeatureT> =
        StrategyStartedHandler { context -> }

    public var strategyFinishedHandler: StrategyFinishedHandler =
        StrategyFinishedHandler { strategyName, result -> }

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
}

public fun interface StrategyStartedHandler<FeatureT : Any> {
    public suspend fun handle(context: StrategyUpdateContext<FeatureT>)
}

public fun interface StrategyFinishedHandler {
    public suspend fun handle(strategyName: String, result: String)
}
