package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.snapshot.providers.NoPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import ai.koog.agents.snapshot.strategy.PersistencyStrategy
import ai.koog.agents.snapshot.strategy.CoordinationStrategy
import ai.koog.agents.snapshot.strategy.CoordinationStrategies
import ai.koog.agents.snapshot.strategy.ProviderRegistry


/**
 * Configuration class for the Snapshot feature.
 */
public class PersistencyFeatureConfig: FeatureConfig() {

    private val registry = ProviderRegistry()
    
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
        replaceWith = ReplaceWith("strategy = PersistencyStrategy.Fixed(CoordinationStrategies.Single(registry.register(storage)))")
    )
    public var storage: PersistencyStorageProvider = NoPersistencyStorageProvider()
        set(value) {
            field = value
            val providerId = registry.register(value, "default")
            // Update strategy when storage is set directly
            _strategy = PersistencyStrategy.Fixed(CoordinationStrategies.Single(providerId))
        }

    private var _strategy: PersistencyStrategy? = null
    
    /**
     * Defines the strategy for selecting persistence providers.
     * 
     * This property supports various strategies:
     * - [PersistencyStrategy.Fixed]: Use a fixed coordination strategy for all agents
     * - [PersistencyStrategy.None]: Disable persistence entirely
     * - [PersistencyStrategy.Dynamic]: Select coordination strategies based on agent context using custom logic
     * - [PersistencyStrategy.AutoSelectForTask]: LLM-powered coordination selection using predefined options
     * 
     * When not explicitly set, defaults to [PersistencyStrategy.Fixed] with a Single coordination
     * using the provider from the [storage] property.
     */
    public var strategy: PersistencyStrategy
        get() = _strategy ?: run {
            val providerId = registry.register(storage, "default")
            PersistencyStrategy.Fixed(CoordinationStrategies.Single(providerId))
        }
        set(value) {
            _strategy = value
            // Update storage for backward compatibility when using Fixed+Single strategy
            if (value is PersistencyStrategy.Fixed && value.coordination is CoordinationStrategies.Single) {
                try {
                    storage = registry.get(value.coordination.provider)
                } catch (e: Exception) {
                    // Provider not found in registry, ignore for backward compatibility
                }
            }
        }
    
    /**
     * Gets the provider registry for registering providers used in coordination strategies.
     */
    public fun getRegistry(): ProviderRegistry = registry

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