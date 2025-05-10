package ai.grazie.code.agents.core.feature.handler

class StrategyHandler<FeatureT : Any>(val feature: FeatureT) {

    /**
     * Handler invoked when a strategy is started. This can be used to perform custom logic
     * related to strategy initiation for a specific feature.
     */
    var strategyStartedHandler: StrategyStartedHandler<FeatureT> =
        StrategyStartedHandler { context -> }

    var strategyFinishedHandler: StrategyFinishedHandler =
        StrategyFinishedHandler { strategyName, result -> }

    /**
     * Handles strategy starts events by delegating to the handler.
     *
     * @param context The context for updating the agent with the feature
     */
    suspend fun handleStrategyStarted(context: StrategyUpdateContext<FeatureT>) {
        strategyStartedHandler.handle(context)
    }

    /**
     * Internal API for handling strategy start events with type casting.
     *
     * @param context The context for updating the agent
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun handleStrategyStartedUnsafe(context: StrategyUpdateContext<*>) {
        handleStrategyStarted(context as StrategyUpdateContext<FeatureT>)
    }
}

fun interface StrategyStartedHandler<FeatureT : Any> {
    suspend fun handle(context: StrategyUpdateContext<FeatureT>)
}

fun interface StrategyFinishedHandler {
    suspend fun handle(strategyName: String, result: String)
}
