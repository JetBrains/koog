package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.snapshot.providers.NoPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import ai.koog.agents.snapshot.strategy.PersistencyStrategy


/**
 * Configuration class for the Snapshot feature.
 */
public class PersistencyFeatureConfig: FeatureConfig() {

    /**
     * Defines the storage mechanism for persisting snapshots in the feature.
     * This property accepts implementations of [PersistencyStorageProvider],
     * which manage how snapshots are stored and retrieved.
     *
     * By default, the storage is set to [NoPersistencyStorageProvider], a no-op
     * implementation that does not persist any data. To enable actual state
     * persistence, assign a custom implementation of [PersistencyStorageProvider]
     * to this property.
     * 
     * @deprecated Use [strategy] instead for more flexible persistence configurations
     */
    @Deprecated(
        message = "Use strategy property instead for more flexible persistence configurations",
        replaceWith = ReplaceWith("strategy = PersistencyStrategy.Single(storage)")
    )
    public var storage: PersistencyStorageProvider = NoPersistencyStorageProvider()
        set(value) {
            field = value
            // Update strategy when storage is set directly
            _strategy = PersistencyStrategy.Single(value)
        }

    private var _strategy: PersistencyStrategy? = null
    
    /**
     * Defines the strategy for selecting persistence providers.
     * 
     * This property supports various strategies:
     * - [PersistencyStrategy.Single]: Use a single provider for all operations (backward compatible)
     * - [PersistencyStrategy.None]: Disable persistence entirely
     * - [PersistencyStrategy.Dynamic]: Select providers based on operation context using custom logic
     * - [PersistencyStrategy.AutoSelectForTask]: LLM-powered provider selection using @LLMDescription annotations
     * 
     * When not explicitly set, defaults to [PersistencyStrategy.Single] with the
     * provider from the [storage] property.
     */
    public var strategy: PersistencyStrategy
        get() = _strategy ?: PersistencyStrategy.Single(storage)
        set(value) {
            _strategy = value
            // Update storage for backward compatibility when using Single strategy
            if (value is PersistencyStrategy.Single) {
                storage = value.provider
            }
        }

    /**
     * Optional factory function for creating custom PersistencyStrategyProvider instances.
     * 
     * When set, this factory will be used instead of the default PersistencyStrategyProvider
     * to create the strategy provider. This enables custom strategy implementations beyond
     * the built-in strategy types.
     * 
     * @param strategy The configured strategy
     * @param context The agent context
     * @return A PersistencyStorageProvider instance (typically a PersistencyStrategyProvider subclass)
     */
    public var strategyProviderFactory: ((PersistencyStrategy, AIAgentContextBase) -> PersistencyStorageProvider)? = null

    /**
     * Controls whether the feature's state should be automatically persisted.
     * When enabled, changes to the checkpoint are saved after each node execution through the assigned
     * [PersistencyStorageProvider], ensuring the state can be restored later.
     *
     * Set this property to `true` to turn on automatic state persistency,
     * or `false` to disable it.
     */
    public var enableAutomaticPersistency: Boolean = false
}